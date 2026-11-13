package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Ten offline, remote-first games. Framework views only; no game engine or network dependency. */
public final class GameHubActivity extends Activity {
    private static final String[] GAMES = {"Chess", "Checkers", "Connect Four", "Reversi",
            "Battleship", "Mastermind", "Peg Solitaire", "Mahjong Solitaire", "Lights Out", "Word Search"};
    private static final int BG = 0xFF101722, PANEL = 0xFF1D2A39, FOCUS = 0xFF315C7B;
    private static final int[] PIECE_VALUE = {0, 100, 320, 330, 500, 900, 20000};
    private final Random random = new Random();
    private LinearLayout root, boardHost;
    private TextView status;
    private Button[][] cells;
    private int game = -1, selected = -1, turn, moves;
    private int[][] board;
    private boolean finished;
    private int castleRights, epRow = -1, epCol = -1;
    private final boolean[][] enemyShots = new boolean[8][8], playerShots = new boolean[8][8];
    private final int[][] enemyFleet = new int[8][8], playerFleet = new int[8][8];
    private int enemyHits, playerHits;
    private final List<int[]> cpuTargets = new ArrayList<>();
    private final int[] secret = new int[4], guess = {-1,-1,-1,-1};
    private final List<String> mastermindHistory = new ArrayList<>();
    private int attempts;
    private final boolean[] foundWords = new boolean[3];
    private static final String[] WORDS = {"PLANET", "ORBIT", "COMET"};
    private int pegCount;
    private int lightsMoves;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(BG);
        showMenu();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(Color.WHITE);
        v.setTextSize(size); v.setGravity(Gravity.CENTER); return v;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setTextSize(15);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        b.setOnClickListener(v -> action.run()); b.setFocusable(true); return b;
    }
    private void base(String heading) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(16), dp(28), dp(16)); root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(button("‹  Games", this::showMenu), new LinearLayout.LayoutParams(dp(145), dp(48)));
        TextView title = label(heading, 26);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(top);
        status = label("", 15); status.setTextColor(0xFFD0DCE8);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(45)));
        boardHost = new LinearLayout(this); boardHost.setGravity(Gravity.CENTER);
        root.addView(boardHost, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private void showMenu() {
        game = -1; selected = -1; base("Game Library");
        status.setText("10 offline games  ·  Navigate with D-pad  ·  OK to open");
        GridLayout menu = new GridLayout(this); menu.setColumnCount(2); menu.setRowCount(5);
        for (int i = 0; i < GAMES.length; i++) {
            final int id = i;
            Button b = button(String.format(java.util.Locale.ROOT, "%02d   %s", i + 1, GAMES[i]),
                    () -> startGame(id));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(78); lp.columnSpec = GridLayout.spec(i % 2, 1f);
            lp.rowSpec = GridLayout.spec(i / 2); lp.setMargins(dp(7), dp(7), dp(7), dp(7));
            menu.addView(b, lp); if (i == 0) b.requestFocus();
        }
        boardHost.addView(menu, new LinearLayout.LayoutParams(dp(850), -2));
    }

    private void startGame(int id) {
        game = id; selected = -1; turn = 0; moves = 0; finished = false;
        for (int y=0;y<8;y++) { Arrays.fill(enemyShots[y],false); Arrays.fill(playerShots[y],false);
            Arrays.fill(enemyFleet[y],0); Arrays.fill(playerFleet[y],0); }
        enemyHits = playerHits = 0; cpuTargets.clear(); attempts = 0; mastermindHistory.clear();
        Arrays.fill(guess,0); Arrays.fill(foundWords,false); lightsMoves = 0;
        switch (id) {
            case 0: initChess(); break;
            case 1: initCheckers(); break;
            case 2: board = new int[6][7]; break;
            case 3: board = new int[8][8]; board[3][3]=2; board[4][4]=2; board[3][4]=1; board[4][3]=1; break;
            case 4: initBattleship(); break;
            case 5: for(int i=0;i<4;i++) secret[i]=random.nextInt(6); break;
            case 6: initPegs(); break;
            case 7: initMahjong(); break;
            case 8: initLightsOut(); break;
            case 9: initWordSearch(); break;
        }
        render();
    }

    // CHESS: legal normal moves, king safety, check, automatic queen promotion, castling and en-passant.
    // The CPU uses a shallow material/position search, not a random legal-move picker.
    private void initChess() {
        board = new int[8][8]; int[] back={4,2,3,5,6,3,2,4};
        for(int x=0;x<8;x++){board[0][x]=back[x];board[1][x]=1;board[6][x]=-1;board[7][x]=-back[x];}
        castleRights=15; epRow=epCol=-1;
    }
    private boolean inside(int y,int x){return y>=0&&y<8&&x>=0&&x<8;}
    private int kingRow(int side){for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(board[y][x]==side*6)return y;return -1;}
    private int kingCol(int side){for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(board[y][x]==side*6)return x;return -1;}
    private boolean attacked(int y,int x,int by) {
        int pawnRow=y+(by<0?1:-1);
        for(int dx:new int[]{-1,1}) if(inside(pawnRow,x+dx)&&board[pawnRow][x+dx]==by) return true;
        int[][] knight={{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for(int[]d:knight)if(inside(y+d[0],x+d[1])&&board[y+d[0]][x+d[1]]==by*2)return true;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dy!=0||dx!=0){
            int yy=y+dy,xx=x+dx,step=1;
            while(inside(yy,xx)){
                int p=board[yy][xx];
                if(p!=0){if(Integer.signum(p)==by){int t=Math.abs(p);
                    if(step==1&&t==6)return true;
                    if((dx==0||dy==0)&&(t==4||t==5))return true;
                    if((dx!=0&&dy!=0)&&(t==3||t==5))return true;
                }break;}
                yy+=dy;xx+=dx;step++;
            }
        }
        return false;
    }
    private boolean inCheck(int side){int y=kingRow(side),x=kingCol(side);return y<0||attacked(y,x,-side);}
    private boolean pseudoChess(int sy,int sx,int y,int x,boolean includeSpecial) {
        if(!inside(y,x)||(sy==y&&sx==x))return false;
        int p=board[sy][sx],target=board[y][x];if(p==0||Math.abs(target)==6||Integer.signum(p)==Integer.signum(target))return false;
        int type=Math.abs(p),dx=x-sx,dy=y-sy,ax=Math.abs(dx),ay=Math.abs(dy),dir=p<0?-1:1;
        if(type==1){
            if(dx==0&&dy==dir&&target==0)return true;
            if(dx==0&&dy==2*dir&&target==0&&((p<0&&sy==6)||(p>0&&sy==1))&&board[sy+dir][sx]==0)return true;
            if(ax==1&&dy==dir&&target!=0)return true;
            if(includeSpecial&&ax==1&&dy==dir&&y==epRow&&x==epCol&&target==0)return true;
            return false;
        }
        if(type==2)return ax==1&&ay==2||ax==2&&ay==1;
        if(type==6&&ax==0&&ay==2&&includeSpecial){
            if(inCheck(dir))return false;
            if(p<0&&sy==7&&sx==4&&y==7&&x==6&&(castleRights&1)!=0&&board[7][5]==0&&board[7][6]==0&&board[7][7]==4&&!attacked(7,5,1)&&!attacked(7,6,1))return true;
            if(p<0&&sy==7&&sx==4&&y==7&&x==2&&(castleRights&2)!=0&&board[7][1]==0&&board[7][2]==0&&board[7][3]==0&&board[7][0]==4&&!attacked(7,3,1)&&!attacked(7,2,1))return true;
            if(p>0&&sy==0&&sx==4&&y==0&&x==6&&(castleRights&4)!=0&&board[0][5]==0&&board[0][6]==0&&board[0][7]==-4&&!attacked(0,5,-1)&&!attacked(0,6,-1))return true;
            if(p>0&&sy==0&&sx==4&&y==0&&x==2&&(castleRights&8)!=0&&board[0][1]==0&&board[0][2]==0&&board[0][3]==0&&board[0][0]==-4&&!attacked(0,3,-1)&&!attacked(0,2,-1))return true;
        }
        if(type==6)return ax<=1&&ay<=1;
        if(type==3&&ax!=ay)return false;
        if(type==4&&dx!=0&&dy!=0)return false;
        if(type==5&&dx!=0&&dy!=0&&ax!=ay)return false;
        int stepx=Integer.compare(dx,0),stepy=Integer.compare(dy,0),cx=sx+stepx,cy=sy+stepy;
        while(cx!=x||cy!=y){if(board[cy][cx]!=0)return false;cx+=stepx;cy+=stepy;}
        return true;
    }
    private int[][] copyBoard(){int[][] c=new int[8][8];for(int y=0;y<8;y++)c[y]=board[y].clone();return c;}
    private void applyChess(int sy,int sx,int y,int x) {
        int p=board[sy][sx],type=Math.abs(p),target=board[y][x];
        if(type==6){if(p<0)castleRights&=~3;else castleRights&=~12;}
        if(type==4){if(sy==7&&sx==0)castleRights&=~2;if(sy==7&&sx==7)castleRights&=~1;
            if(sy==0&&sx==0)castleRights&=~8;if(sy==0&&sx==7)castleRights&=~4;}
        if(Math.abs(target)==4){if(y==7&&x==0)castleRights&=~2;if(y==7&&x==7)castleRights&=~1;
            if(y==0&&x==0)castleRights&=~8;if(y==0&&x==7)castleRights&=~4;}
        epRow=epCol=-1;
        if(type==1&&x!=sx&&target==0){board[y+(p<0?1:-1)][x]=0;}
        if(type==6&&Math.abs(x-sx)==2){
            if(x==6){board[y][5]=board[y][7];board[y][7]=0;}
            else {board[y][3]=board[y][0];board[y][0]=0;}
        }
        board[y][x]=p;board[sy][sx]=0;
        if(type==1&&Math.abs(y-sy)==2){epRow=(y+sy)/2;epCol=x;}
        if(type==1&&(y==0||y==7))board[y][x]=p<0?-5:5;
    }
    private boolean legalChess(int sy,int sx,int y,int x) {
        if(!pseudoChess(sy,sx,y,x,true))return false;
        int[][] old=copyBoard();int oldRights=castleRights,oldEr=epRow,oldEc=epCol;
        int side=Integer.signum(board[sy][sx]);applyChess(sy,sx,y,x);boolean ok=!inCheck(side);
        board=old;castleRights=oldRights;epRow=oldEr;epCol=oldEc;return ok;
    }
    private List<int[]> chessMoves(int side) {
        List<int[]> out=new ArrayList<>();
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(Integer.signum(board[y][x])==side)
            for(int yy=0;yy<8;yy++)for(int xx=0;xx<8;xx++)if(legalChess(y,x,yy,xx))out.add(new int[]{y,x,yy,xx});
        return out;
    }
    private int chessEval() {
        int score=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){int p=board[y][x];if(p!=0){int v=PIECE_VALUE[Math.abs(p)];int center=7-(Math.abs(3-y)+Math.abs(3-x));score+=Integer.signum(p)*(v+(Math.abs(p)<=5?center*3:0));}}
        return score;
    }
    private int chessSearch(int side,int depth,int alpha,int beta) {
        List<int[]> moves=chessMoves(side);
        if(depth==0||moves.isEmpty())return side*chessEval()-(moves.isEmpty()&&inCheck(side)?100000:0);
        int best=-1000000;
        for(int[]m:moves){
            int[][]old=copyBoard();int cr=castleRights,er=epRow,ec=epCol;
            applyChess(m[0],m[1],m[2],m[3]);
            int value=-chessSearch(-side,depth-1,-beta,-alpha);
            board=old;castleRights=cr;epRow=er;epCol=ec;
            if(value>best)best=value;if(value>alpha)alpha=value;if(alpha>=beta)break;
        }
        return best;
    }
    private void aiChess() {
        List<int[]> moves=chessMoves(1);if(moves.isEmpty())return;
        int best=-1000000;List<int[]> bestMoves=new ArrayList<>();
        for(int[]m:moves){
            int[][]old=copyBoard();int cr=castleRights,er=epRow,ec=epCol;applyChess(m[0],m[1],m[2],m[3]);
            int v=-chessSearch(-1,1,-1000000,1000000);
            board=old;castleRights=cr;epRow=er;epCol=ec;
            if(v>best){best=v;bestMoves.clear();}if(v==best)bestMoves.add(m);
        }
        int[]m=bestMoves.get(random.nextInt(bestMoves.size()));applyChess(m[0],m[1],m[2],m[3]);
    }
    private void chessAfterPlayer() {
        if(chessMoves(1).isEmpty()){finished=true;status.setText(inCheck(1)?"Checkmate · You win":"Draw · Stalemate");return;}
        aiChess();
        if(chessMoves(-1).isEmpty()){finished=true;status.setText(inCheck(-1)?"Checkmate · CPU wins":"Draw · Stalemate");}
        else status.setText(inCheck(-1)?"CPU is in check · Your move":"Your move · White CPU");
    }

    // CHECKERS: mandatory captures, kings, promotion and multi-jump continuation.
    private boolean checkersKingJump;
    private void initCheckers(){board=new int[8][8];for(int y=0;y<3;y++)for(int x=0;x<8;x++)if((x+y)%2==1)board[y][x]=1;for(int y=5;y<8;y++)for(int x=0;x<8;x++)if((x+y)%2==1)board[y][x]=-1;checkersKingJump=false;}
    private List<int[]> checkerMoves(int side,boolean capturesOnly) {
        List<int[]> all=new ArrayList<>(),caps=new ArrayList<>();
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(Integer.signum(board[y][x])==side){
            int piece=board[y][x],king=Math.abs(piece)==2?1:0;
            for(int dx:new int[]{-1,1})for(int dir:new int[]{-1,1}){
                if(!king&&dir!=(side<0?-1:1))continue;
                int ny=y+dir,nx=x+dx;
                if(inside(ny,nx)&&board[ny][nx]==0)all.add(new int[]{y,x,ny,nx});
                int jy=y+2*dir,jx=x+2*dx;
                if(inside(jy,jx)&&inside(ny,nx)&&board[ny][nx]*side<0&&board[jy][jx]==0)caps.add(new int[]{y,x,jy,jx});
            }
        }
        return capturesOnly?caps:!caps.isEmpty()?caps:all;
    }
    private boolean hasCaptureFrom(int y,int x,int side) {
        int piece=board[y][x],king=Math.abs(piece)==2?1:0;
        for(int dx:new int[]{-1,1})for(int dir:new int[]{-1,1}){
            if(!king&&dir!=(side<0?-1:1))continue;
            int ny=y+dir,nx=x+dx,jy=y+2*dir,jx=x+2*dx;
            if(inside(jy,jx)&&inside(ny,nx)&&board[ny][nx]*side<0&&board[jy][jx]==0)return true;
        }return false;
    }
    private void doCheckerMove(int[]m,int side) {
        int p=board[m[0]][m[1]],dy=m[2]-m[0];
        if(Math.abs(dy)==2)board[(m[0]+m[2])/2][(m[1]+m[3])/2]=0;
        board[m[2]][m[3]]=p;board[m[0]][m[1]]=0;
        if(side<0&&m[2]==0)board[m[2]][m[3]]=-2;
        if(side>0&&m[2]==7)board[m[2]][m[3]]=2;
    }
    private void checkerAiTurn() {
        int side=1;
        for(int n=0;n<30;n++){
            List<int[]> m=checkerMoves(side,false);if(m.isEmpty())break;
            // Prefer jumps, then moves toward promotion and the board centre.
            int best=-999;List<int[]> picks=new ArrayList<>();
            for(int[]q:m){int score=Math.abs(q[2]-q[0])==2?100:0;score+=q[2]*3+(3-Math.abs(3-q[3]));
                int p=board[q[0]][q[1]];if(Math.abs(p)==2)score+=8;
                if(score>best){best=score;picks.clear();}if(score==best)picks.add(q);}
            int[]q=picks.get(random.nextInt(picks.size()));doCheckerMove(q,side);
            if(Math.abs(q[2]-q[0])!=2||!hasCaptureFrom(q[2],q[3],side))break;
        }
    }

    // CONNECT FOUR: immediate win, mandatory block, then centre-weighted one-ply evaluation.
    private boolean fourWin(int who) {
        for(int y=0;y<6;y++)for(int x=0;x<7;x++)if(board[y][x]==who)
            for(int[]d:new int[][]{{0,1},{1,0},{1,1},{1,-1}}){int n=1;for(int k=1;k<4;k++){int yy=y+d[0]*k,xx=x+d[1]*k;if(yy<0||yy>=6||xx<0||xx>=7||board[yy][xx]!=who)break;n++;}if(n==4)return true;}
        return false;
    }
    private int landing(int col){for(int y=5;y>=0;y--)if(board[y][col]==0)return y;return -1;}
    private void connectCpu() {
        int chosen=-1;
        for(int c=0;c<7;c++){int y=landing(c);if(y>=0){board[y][c]=2;if(fourWin(2))chosen=c;board[y][c]=0;if(chosen>=0)break;}}
        if(chosen<0)for(int c=0;c<7;c++){int y=landing(c);if(y>=0){board[y][c]=1;if(fourWin(1))chosen=c;board[y][c]=0;if(chosen>=0)break;}}
        if(chosen<0){int[]order={3,2,4,1,5,0,6};for(int c:order)if(landing(c)>=0){chosen=c;break;}}
        if(chosen>=0){board[landing(chosen)][chosen]=2;if(fourWin(2)){finished=true;status.setText("CPU wins");}}
    }

    // REVERSI: legal flips, pass handling and end-of-game score.
    private List<int[]> reversiMoves(int side){List<int[]>m=new ArrayList<>();for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(board[y][x]==0&&reversiCanPlace(y,x,side))m.add(new int[]{y,x});return m;}
    private boolean reversiCanPlace(int y,int x,int side){for(int[]d:dirs8()){int yy=y+d[0],xx=x+d[1],n=0;while(inside(yy,xx)&&board[yy][xx]==3-side){n++;yy+=d[0];xx+=d[1];}if(n>0&&inside(yy,xx)&&board[yy][xx]==side)return true;}return false;}
    private int[][] dirs8(){return new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};}
    private void reversiPlace(int y,int x,int side){board[y][x]=side;for(int[]d:dirs8()){int yy=y+d[0],xx=x+d[1];List<int[]>line=new ArrayList<>();while(inside(yy,xx)&&board[yy][xx]==3-side){line.add(new int[]{yy,xx});yy+=d[0];xx+=d[1];}if(inside(yy,xx)&&board[yy][xx]==side)for(int[]q:line)board[q[0]][q[1]]=side;}}
    private int reversiHeuristic(){
        int discs=0,corner=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){if(board[y][x]==2)discs++;else if(board[y][x]==1)discs--;}
        int[][]corners={{0,0},{0,7},{7,0},{7,7}};
        for(int[]q:corners){if(board[q[0]][q[1]]==2)corner+=24;else if(board[q[0]][q[1]]==1)corner-=24;}
        return discs+corner+(reversiMoves(2).size()-reversiMoves(1).size())*3;
    }
    private void reversiCpu(){
        List<int[]>m=reversiMoves(2);
        if(m.isEmpty()){if(reversiMoves(1).isEmpty())endReversi();else status.setText("CPU passes · Your turn");return;}
        int bestScore=Integer.MIN_VALUE;List<int[]>best=new ArrayList<>();
        for(int[]q:m){
            int[][]before=copyBoard();reversiPlace(q[0],q[1],2);
            int score=reversiHeuristic();board=before;
            if(score>bestScore){bestScore=score;best.clear();}
            if(score==bestScore)best.add(q);
        }
        int[]move=best.get(random.nextInt(best.size()));reversiPlace(move[0],move[1],2);
        if(reversiMoves(1).isEmpty()){
            if(reversiMoves(2).isEmpty())endReversi();
            else {status.setText("You pass · CPU plays again");reversiCpu();}
        } else status.setText("Your turn");
    }
    private void endReversi(){int one=0,two=0;for(int[]r:board)for(int v:r){if(v==1)one++;if(v==2)two++;}finished=true;status.setText("Game over · You "+one+" : "+two+" CPU");}

    // BATTLESHIP: standard four-ship fleet (4,3,3,2), alternating turns and target-seeking CPU.
    private static final int[] SHIP_SIZES={4,3,3,2};
    private void initBattleship(){placeFleet(enemyFleet);placeFleet(playerFleet);}
    private void placeFleet(int[][] target){
        for(int size:SHIP_SIZES){boolean done=false;for(int tries=0;tries<200&&!done;tries++){
            boolean vertical=random.nextBoolean();int y=random.nextInt(vertical?9-size:8),x=random.nextInt(vertical?8:9-size);
            boolean free=true;for(int k=0;k<size;k++)if(target[y+(vertical?k:0)][x+(vertical?0:k)]!=0)free=false;
            if(free){for(int k=0;k<size;k++)target[y+(vertical?k:0)][x+(vertical?0:k)]=1;done=true;}
        }}
    }
    private void cpuFire(){
        int[]shot=null;
        while(!cpuTargets.isEmpty()&&shot==null){int[]q=cpuTargets.remove(0);if(!playerShots[q[0]][q[1]])shot=q;}
        if(shot==null){List<int[]>available=new ArrayList<>();for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(!playerShots[y][x])available.add(new int[]{y,x});if(!available.isEmpty())shot=available.get(random.nextInt(available.size()));}
        if(shot!=null){int y=shot[0],x=shot[1];playerShots[y][x]=true;if(playerFleet[y][x]==1){playerFleet[y][x]=2;playerHits++;for(int[]d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})if(inside(y+d[0],x+d[1])&&!playerShots[y+d[0]][x+d[1]])cpuTargets.add(new int[]{y+d[0],x+d[1]});}}
    }
    private int totalEnemy(){int n=0;for(int[]r:enemyFleet)for(int v:r)if(v==1||v==2)n++;return n;}
    private int totalPlayer(){int n=0;for(int[]r:playerFleet)for(int v:r)if(v==1||v==2)n++;return n;}

    // MASTERMIND: 4 pegs, 6 colours, duplicate-safe exact/misplaced feedback and an attempt history.
    private String mastermindLine(int[] code,int a,int exact,int near){StringBuilder b=new StringBuilder();for(int v:code)b.append(v+1);return "Guess "+a+": "+b+"  ·  "+exact+" exact / "+near+" misplaced";}
    private void submitMastermind(){
        if(finished)return;
        attempts++;int exact=0,near=0;boolean[]sc=new boolean[4],gc=new boolean[4];
        for(int i=0;i<4;i++)if(guess[i]==secret[i]){exact++;sc[i]=true;gc[i]=true;}
        for(int i=0;i<4;i++)if(!gc[i])for(int j=0;j<4;j++)if(!sc[j]&&guess[i]==secret[j]){near++;sc[j]=true;break;}
        mastermindHistory.add(mastermindLine(guess,attempts,exact,near));
        if(exact==4){finished=true;status.setText("Code cracked in "+attempts+" guesses");}
        else if(attempts>=10){finished=true;status.setText("No more guesses · Code: "+codeText(secret));}
        renderMastermindHistory();
    }
    private String codeText(int[]v){StringBuilder b=new StringBuilder();for(int x:v)b.append(x+1);return b.toString();}

    // PEG SOLITAIRE: standard English 33-hole cross, orthogonal jumps, one-peg objective.
    private void initPegs(){board=new int[7][7];pegCount=0;for(int y=0;y<7;y++)for(int x=0;x<7;x++)if((x>=2&&x<=4)||(y>=2&&y<=4)){board[y][x]=1;pegCount++;}board[3][3]=0;pegCount--;}
    private boolean pegCell(int y,int x){return y>=0&&y<7&&x>=0&&x<7&&((x>=2&&x<=4)||(y>=2&&y<=4));}
    private boolean hasPegMove(){for(int y=0;y<7;y++)for(int x=0;x<7;x++)if(board[y][x]==1)for(int[]d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int yy=y+2*d[0],xx=x+2*d[1];if(pegCell(yy,xx)&&board[y+d[0]][x+d[1]]==1&&board[yy][xx]==0)return true;}return false;}

    // MAHJONG SOLITAIRE: flat TV-friendly layout, four copies of six symbols; only free pairs can be removed.
    private void initMahjong(){board=new int[6][8];List<Integer>t=new ArrayList<>();for(int v=1;v<=6;v++)for(int n=0;n<8;n++)t.add(v);Collections.shuffle(t,random);int k=0;for(int y=0;y<6;y++)for(int x=0;x<8;x++)board[y][x]=t.get(k++);}
    private boolean mahjongFree(int y,int x){if(board[y][x]==0)return false;boolean left=x==0||board[y][x-1]==0,right=x==7||board[y][x+1]==0;return left||right;}
    private void shuffleMahjongRemaining(){
        List<Integer>left=new ArrayList<>();
        for(int[]r:board)for(int v:r)if(v!=0)left.add(v);
        for(int attempt=0;attempt<100;attempt++){
            Collections.shuffle(left,random);int k=0;
            for(int y=0;y<6;y++)for(int x=0;x<8;x++)if(board[y][x]!=0)board[y][x]=left.get(k++);
            if(mahjongMoves())return;
        }
    }
    private boolean mahjongMoves(){for(int y=0;y<6;y++)for(int x=0;x<8;x++)if(mahjongFree(y,x))for(int yy=0;yy<6;yy++)for(int xx=0;xx<8;xx++)if((yy!=y||xx!=x)&&board[yy][xx]==board[y][x]&&mahjongFree(yy,xx))return true;return false;}

    // LIGHTS OUT: every generated board is solvable because it starts solved and is scrambled by legal taps.
    private void initLightsOut(){board=new int[5][5];for(int i=0;i<28;i++)flipLights(random.nextInt(5),random.nextInt(5));}
    private void flipLights(int y,int x){board[y][x]^=1;for(int[]d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int yy=y+d[0],xx=x+d[1];if(yy>=0&&yy<5&&xx>=0&&xx<5)board[yy][xx]^=1;}}
    private boolean lightsSolved(){for(int[]r:board)for(int v:r)if(v!=0)return false;return true;}

    // WORD SEARCH: words are hidden in all eight directions; the list is shown, not the solution coordinates.
    private void initWordSearch(){
        board=new int[8][10];
        for(String word:WORDS){boolean placed=false;for(int n=0;n<300&&!placed;n++){
            int y=random.nextInt(8),x=random.nextInt(10);int[]dir=dirs8()[random.nextInt(8)];
            int ey=y+dir[0]*(word.length()-1),ex=x+dir[1]*(word.length()-1);
            if(!insideWord(ey,ex))continue;
            boolean free=true;for(int k=0;k<word.length();k++)if(board[y+dir[0]*k][x+dir[1]*k]!=0)free=false;
            if(!free)continue;
            for(int k=0;k<word.length();k++)board[y+dir[0]*k][x+dir[1]*k]=word.charAt(k);
            placed=true;
        }}
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for(int y=0;y<8;y++)for(int x=0;x<10;x++)if(board[y][x]==0)board[y][x]=alphabet.charAt(random.nextInt(26));
    }
    private boolean insideWord(int y,int x){return y>=0&&y<8&&x>=0&&x<10;}
    private String selectedWord(int a,int b){int y=a/10,x=a%10,ey=b/10,ex=b%10,dy=Integer.compare(ey,y),dx=Integer.compare(ex,x);
        if(y!=ey&&x!=ex&&Math.abs(ey-y)!=Math.abs(ex-x))return "";
        if(y==ey&&x==ex)return "";
        StringBuilder s=new StringBuilder();while(true){s.append((char)board[y][x]);if(y==ey&&x==ex)break;y+=dy;x+=dx;if(!insideWord(y,x))return "";}return s.toString();}

    private void render() {
        base(GAMES[game]);
        if(game==5){renderMastermind();return;}
        if(game==9)status.setText(wordListText());
        else if(game==4)status.setText(battleshipStatus());
        else status.setText(gameStatus());
        int rows=board.length,cols=board[0].length,max=Math.max(rows,cols);
        int available=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-dp(game==9?180:160);
        int side=Math.min(dp(66),Math.max(dp(30),available/max-dp(4)));
        GridLayout grid=new GridLayout(this);grid.setRowCount(rows);grid.setColumnCount(cols);
        cells=new Button[rows][cols];
        for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
            final int yy=y,xx=x;Button b=button(cellText(y,x),()->tap(yy,xx));
            b.setPadding(0,0,0,0);b.setTextSize(game==0?20:17);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=side;lp.height=side;
            lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(b,lp);cells[y][x]=b;
        }
        boardHost.addView(grid);
        if(rows>0&&cols>0){
            if(game==0)cells[6][0].requestFocus();
            else if(game==1)cells[5][0].requestFocus();
            else if(game==3)cells[2][4].requestFocus();
            else if(game==6)cells[2][3].requestFocus();
            else cells[0][0].requestFocus();
        }
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        actions.addView(button("New game",()->startGame(game)),new LinearLayout.LayoutParams(dp(150),dp(44)));
        root.addView(actions);
    }
    private String wordListText(){StringBuilder s=new StringBuilder("Find: ");for(int i=0;i<3;i++){if(foundWords[i])s.append("✓ ");else s.append(WORDS[i]).append("  ");}return s.toString();}
    private String gameStatus(){
        switch(game){
            case 0:return "Your move · Select a black piece, then its destination";
            case 1:return "Your turn · Select a checker, then a diagonal destination";
            case 2:return "Your turn · Choose a column to drop your disc";
            case 3:return "Your turn · Choose a legal empty square";
            case 6:return "Jump a peg over another · Remove all but one";
            case 7:return "Select two equal, free tiles (left or right side open)";
            case 8:return "Switch off every light · Moves: "+lightsMoves;
            default:return "Use the D-pad and OK";
        }
    }
    private String battleshipStatus(){return "Enemy ship cells hit: "+enemyHits+"/"+totalEnemy()+" · Your fleet hit: "+playerHits+"/"+totalPlayer()+" · Choose a target";}

    private String cellText(int y,int x){
        if(game==6&&!pegCell(y,x))return "";
        int v=board[y][x];
        switch(game){
            case 0:{String[]p={"","♟","♞","♝","♜","♛","♚"};return v==0?"":(v<0?"":"")+p[Math.abs(v)];}
            case 1:return v==0?"":Math.abs(v)==2?(v>0?"♛":"♕"):(v>0?"●":"○");
            case 2:return v==0?"·":v==1?"●":"○";
            case 3:return v==0?"·":v==1?"●":"○";
            case 4:return enemyShots[y][x]?(enemyFleet[y][x]==2?"✕":"•"):"·";
            case 6:return v==1?"●":"";
            case 7:return v==0?"":new String[]{"","◆","●","▲","■","✦","✚"}[v];
            case 8:return v==1?"●":"";
            case 9:return String.valueOf((char)v);
            default:return "";
        }
    }
    private void tap(int y,int x) {
        if(finished)return;
        if(game==2){connectDrop(x);return;}
        if(game==4){battleshipShot(y,x);return;}
        if(game==5)return;
        if(game==8){flipLights(y,x);lightsMoves++;if(lightsSolved()){finished=true;status.setText("Solved in "+lightsMoves+" moves");}else status.setText("Moves: "+lightsMoves);refreshCells();return;}
        if(game==9){wordTap(y,x);return;}
        if(game==6){pegTap(y,x);return;}
        if(game==7){mahjongTap(y,x);return;}
        if(game==3){
            if(board[y][x]==0&&reversiCanPlace(y,x,1)){
                reversiPlace(y,x,1);
                if(reversiMoves(2).isEmpty()){
                    if(reversiMoves(1).isEmpty())endReversi();
                    else status.setText("CPU passes · Your turn");
                } else reversiCpu();
            } else status.setText("That square is not a legal move");
            refreshCells();return;
        }
        if(selected<0){
            if((game==0&&board[y][x]<0)||(game==1&&board[y][x]<0)){selected=y*8+x;refreshCells();}
            return;
        }
        int sy=selected/(game==3?8:8),sx=selected%8;selected=-1;
        if(game==0){
            if(legalChess(sy,sx,y,x)){applyChess(sy,sx,y,x);chessAfterPlayer();}
            else status.setText("Illegal move · Select a legal destination");
        } else if(game==1) {
            int side=-1;List<int[]>legal=checkerMoves(side,false);boolean forcedPiece=false;
            for(int[]m:legal)if(m[0]==sy&&m[1]==sx)forcedPiece=true;
            boolean moved=false;
            for(int[]m:legal)if(m[0]==sy&&m[1]==sx&&m[2]==y&&m[3]==x){
                doCheckerMove(m,side);moved=true;
                if(Math.abs(m[2]-m[0])==2&&hasCaptureFrom(y,x,side)){selected=y*8+x;status.setText("Continue the jump with the same checker");}
                else checkerAiTurn();
                break;
            }
            if(!moved)status.setText(forcedPiece?"Illegal move · Complete a capture":"Select a checker with a legal move");
            if(moved&&selected<0){
                if(checkerMoves(-1,false).isEmpty()){finished=true;status.setText("CPU wins · No legal moves");}
                else if(checkerMoves(1,false).isEmpty()){finished=true;status.setText("You win · CPU has no legal moves");}
            }
        } else if(game==3) {
            if(board[y][x]==0&&reversiCanPlace(y,x,1)){reversiPlace(y,x,1);if(reversiMoves(2).isEmpty()){if(reversiMoves(1).isEmpty())endReversi();else status.setText("CPU passes · Your turn");}else reversiCpu();}
            else status.setText("That square is not a legal move");
        }
        refreshCells();
    }
    private boolean connectBoardFull(){for(int x=0;x<7;x++)if(landing(x)>=0)return false;return true;}
    private void connectDrop(int col){
        int y=landing(col);if(y<0)return;board[y][col]=1;moves++;
        if(fourWin(1)){finished=true;status.setText("You win!");}
        else {
            connectCpu();
            if(!finished&&connectBoardFull()){finished=true;status.setText("Draw · Board full");}
            else if(!finished)status.setText("Your move · CPU has played");
        }
        refreshCells();
    }
    private void battleshipShot(int y,int x){
        if(enemyShots[y][x]){status.setText("Already fired at that square");return;}
        enemyShots[y][x]=true;moves++;
        if(enemyFleet[y][x]==1){enemyFleet[y][x]=2;enemyHits++;}
        if(enemyHits>=totalEnemy()){finished=true;status.setText("You sank the entire fleet in "+moves+" shots!");}
        else {cpuFire();if(playerHits>=totalPlayer()){finished=true;status.setText("Your fleet was sunk · New game to play again");}
            else status.setText(battleshipStatus());}
        refreshCells();
    }
    private void pegTap(int y,int x){
        if(!pegCell(y,x))return;
        if(selected<0){if(board[y][x]==1){selected=y*7+x;refreshCells();}return;}
        int sy=selected/7,sx=selected%7;selected=-1;
        if(Math.abs(y-sy)+Math.abs(x-sx)==2&&board[sy][sx]==1&&board[y][x]==0){
            int my=(sy+y)/2,mx=(sx+x)/2;if(board[my][mx]==1){board[sy][sx]=board[my][mx]=0;board[y][x]=1;pegCount--;}}
        if(pegCount==1){finished=true;status.setText("Perfect · One peg remains");}
        else if(!hasPegMove()){finished=true;status.setText("No moves remain · "+pegCount+" pegs left");}
        else status.setText("Pegs remaining: "+pegCount);
        refreshCells();
    }
    private void mahjongTap(int y,int x){
        if(board[y][x]==0){return;}
        if(selected<0){if(mahjongFree(y,x)){selected=y*8+x;refreshCells();}else status.setText("Tile is blocked on both sides");return;}
        int sy=selected/8,sx=selected%8;
        if(sy==y&&sx==x){selected=-1;refreshCells();return;}
        selected=-1;
        if(board[y][x]==board[sy][sx]&&mahjongFree(y,x)&&mahjongFree(sy,sx)){board[y][x]=board[sy][sx]=0;moves++;
            int remaining=0;for(int[]r:board)for(int v:r)if(v!=0)remaining++;
            if(remaining==0){finished=true;status.setText("Board cleared!");}
            else if(!mahjongMoves()){shuffleMahjongRemaining();status.setText("No free pairs · Remaining tiles reshuffled");}
            else status.setText("Matched · "+remaining+" tiles remain");
        }else status.setText("Choose an equal tile with an open side");
        refreshCells();
    }
    private void wordTap(int y,int x){
        if(selected<0){selected=y*10+x;refreshCells();return;}
        int end=y*10+x;String word=selectedWord(selected,end);selected=-1;boolean hit=false;
        for(int i=0;i<WORDS.length;i++)if(!foundWords[i]&&(word.equals(WORDS[i])||new StringBuilder(word).reverse().toString().equals(WORDS[i]))){foundWords[i]=true;hit=true;}
        if(hit){int count=0;for(boolean f:foundWords)if(f)count++;if(count==3){finished=true;status.setText("All words found!");}else status.setText(wordListText());}
        else status.setText("No matching word along that line");
        refreshCells();
    }
    private void refreshCells(){
        if(cells==null)return;
        for(int y=0;y<cells.length;y++)for(int x=0;x<cells[y].length;x++){
            Button b=cells[y][x];b.setText(cellText(y,x));
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected==(y*(game==9?10:game==6?7:8)+x)?FOCUS:PANEL));
        }
    }
    private void renderMastermind(){
        status.setText("Tap each slot to cycle 1–6 · Duplicates allowed · 10 attempts");
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button[] slots=new Button[4];
        for(int i=0;i<4;i++){final int k=i;slots[i]=button("Slot "+(i+1)+"  ·  "+(guess[i]+1),()->{guess[k]=(guess[k]+1)%6;slots[k].setText("Slot "+(k+1)+"  ·  "+(guess[k]+1));});row.addView(slots[i],new LinearLayout.LayoutParams(dp(145),dp(72)));}
        body.addView(row);body.addView(button("Submit guess",()->submitMastermind()),new LinearLayout.LayoutParams(dp(220),dp(54)));
        TextView history=label("",14);history.setGravity(Gravity.CENTER);history.setTextColor(0xFFD7E2ED);history.setTag("history");
        body.addView(history,new LinearLayout.LayoutParams(-1,dp(170)));
        boardHost.addView(body,new LinearLayout.LayoutParams(dp(700),-2));
        renderMastermindHistory();
    }
    private void renderMastermindHistory(){
        if(boardHost==null)return;TextView h=(TextView)boardHost.findViewWithTag("history");if(h==null)return;
        StringBuilder s=new StringBuilder();int from=Math.max(0,mindHistorySize()-5);
        for(int i=from;i<mindHistorySize();i++)s.append(mastermindHistory.get(i)).append('\n');
        h.setText(s.toString());
    }
    private int mindHistorySize(){return mastermindHistory.size();}

    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_BACK){if(game>=0){showMenu();return true;}}
        return super.onKeyDown(key,event);
    }
}
