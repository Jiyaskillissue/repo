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
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Ten compact, offline games for TV remotes. Uses framework widgets only to stay light on memory. */
public final class GameHubActivity extends Activity {
    private static final String[] GAMES={"Chess","Checkers","Connect Four","Reversi","Battleship","Mastermind","Peg Solitaire","Mahjong Solitaire","Lights Out","Word Search"};
    private static final int BG=0xFF101722,PANEL=0xFF1D2A39;
    private final Random random=new Random();
    private LinearLayout root,boardHost;
    private TextView title,status;
    private Button[][] cells;
    private int game=-1,selected=-1,turn=0,moves=0;
    private int[][] board;
    private boolean finished;
    private final int[] secret=new int[4],guess={-1,-1,-1,-1};
    private int attempts;
    private final boolean[][] hits=new boolean[8][8];
    private String word="PLANET";
    private final String[] words={"PLANET","ORBIT","COMET"};

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().getDecorView().setBackgroundColor(BG);showMenu();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);return v;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(v->r.run());b.setFocusable(true);return b;}
    private void base(String heading){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(28),dp(18),dp(28),dp(18));root.setBackgroundColor(BG);setContentView(root);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(button("‹ Games",this::showMenu),new LinearLayout.LayoutParams(dp(140),dp(52)));title=label(heading,27);top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(top);status=label("",16);status.setTextColor(0xFFD0DCE8);root.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));boardHost=new LinearLayout(this);boardHost.setGravity(Gravity.CENTER);root.addView(boardHost,new LinearLayout.LayoutParams(-1,0,1));}
    private void showMenu(){game=-1;base("Game Library");status.setText("10 offline games · Use D-pad and OK");LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);boardHost.addView(list,new LinearLayout.LayoutParams(dp(620),-2));for(int i=0;i<GAMES.length;i++){final int id=i;Button b=button(String.format("%02d   %s",i+1,GAMES[i]),()->startGame(id));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.setMargins(0,dp(2),0,dp(2));list.addView(b,p);if(i==0)b.requestFocus();}}
    private void startGame(int id){game=id;selected=-1;turn=moves=attempts=0;finished=false;for(int y=0;y<8;y++)Arrays.fill(hits[y],false);Arrays.fill(guess,-1);switch(id){case 0:initChess();break;case 1:initCheckers();break;case 2:board=new int[6][7];break;case 3:board=new int[8][8];board[3][3]=2;board[4][4]=2;board[3][4]=1;board[4][3]=1;break;case 4:board=new int[8][8];for(int x=0;x<8;x++)board[1][x]=1;break;case 5:for(int i=0;i<4;i++)secret[i]=random.nextInt(6);break;case 6:initPegs();break;case 7:initMahjong();break;case 8:board=new int[5][5];for(int y=0;y<5;y++)for(int x=0;x<5;x++)board[y][x]=random.nextBoolean()?1:0;break;case 9:initWords();break;}render();}
    private void initChess(){board=new int[8][8];int[] back={4,2,3,5,6,3,2,4};for(int x=0;x<8;x++){board[0][x]=back[x];board[1][x]=1;board[6][x]=-1;board[7][x]=-back[x];}}
    private void initCheckers(){board=new int[8][8];for(int y=0;y<3;y++)for(int x=0;x<8;x++)if((x+y)%2==1)board[y][x]=1;for(int y=5;y<8;y++)for(int x=0;x<8;x++)if((x+y)%2==1)board[y][x]=-1;}
    private void initPegs(){board=new int[7][7];for(int y=0;y<7;y++)for(int x=0;x<7;x++)if((x>=2&&x<=4)||(y>=2&&y<=4))board[y][x]=1;board[3][3]=0;}
    private void initMahjong(){board=new int[6][8];List<Integer> t=new ArrayList<>();for(int i=0;i<24;i++){t.add(i%6+1);t.add(i%6+1);}Collections.shuffle(t,random);int k=0;for(int y=0;y<6;y++)for(int x=0;x<8;x++)board[y][x]=t.get(k++);}
    private void initWords(){board=new int[8][8];String a="ABCDEFGHIJKLMNOPQRSTUVWXYZ";for(int y=0;y<8;y++)for(int x=0;x<8;x++)board[y][x]=a.charAt(random.nextInt(26));word=words[random.nextInt(words.length)];int row=random.nextInt(8),col=random.nextInt(9-word.length());for(int i=0;i<word.length();i++)board[row][col+i]=word.charAt(i);}
    private void render(){base(GAMES[game]);if(game==5){renderMastermind();return;}if(game==9)status.setText("Find "+word+" · Select its first and last letters");else if(game==4)status.setText("Find all 8 ship sections · Shots: "+moves);else status.setText(gameStatus());int rows=board.length,cols=board[0].length,max=Math.max(rows,cols),side=Math.min(dp(560)/max,dp(66));GridLayout grid=new GridLayout(rows,cols);cells=new Button[rows][cols];for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){final int yy=y,xx=x;Button b=button(cellText(y,x),()->tap(yy,xx));b.setPadding(0,0,0,0);b.setTextSize(game==0?21:18);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=side;lp.height=side;lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(b,lp);cells[y][x]=b;}boardHost.addView(grid);cells[0][0].requestFocus();root.addView(button("New game",()->startGame(game)),new LinearLayout.LayoutParams(dp(170),dp(46)));}
    private String gameStatus(){switch(game){case 0:return "White to move · Select a piece, then destination";case 1:return "Your turn · Select a checker, then diagonal destination";case 2:return "Select a column to drop";case 3:return "Select an empty square";case 6:return "Jump a peg over another · Leave one peg";case 7:return "Match equal tiles";case 8:return "Toggle lights until all are dark";default:return "D-pad and OK to play";}}
    private String cellText(int y,int x){int v=board[y][x];switch(game){case 0:{String[]p={"","♟","♞","♝","♜","♛","♚"};return v==0?"":p[Math.abs(v)];}case 1:return v==0?"":v>0?"●":"○";case 2:return v==0?"·":v==1?"●":"○";case 3:return v==0?"·":v==1?"●":"○";case 4:return hits[y][x]?(v==1?"✕":"•"):"?";case 6:return v==1?"●":"";case 7:return v==0?"":new String[]{"","◆","●","▲","■","✦","✚"}[v];case 8:return v==1?"●":"";case 9:return String.valueOf((char)v);default:return "";}}
    private void tap(int y,int x){if(finished)return;if(game==2){drop(x);return;}if(game==4){if(!hits[y][x]){hits[y][x]=true;moves++;if(board[y][x]==1)turn++;if(turn>=8){finished=true;status.setText("Fleet found!");}else status.setText("Hits "+turn+"/8 · Shots "+moves);}refreshCells();return;}if(game==8){flip(y,x);return;}if(game==9){if(selected<0){selected=y*8+x;status.setText("Start selected · Choose the end of the word");}else{String s=wordAt(selected,y*8+x);if(s.equals(word)||new StringBuilder(s).reverse().toString().equals(word)){finished=true;status.setText("Word found!");}else{selected=-1;status.setText("Not found. Try again.");}}refreshCells();return;}if(selected<0){if(board[y][x]!=0){selected=y*8+x;refreshCells();}return;}int sy=selected/8,sx=selected%8;selected=-1;if(game==6){if(Math.abs(y-sy)+Math.abs(x-sx)==2){int my=(y+sy)/2,mx=(x+sx)/2;if(board[sy][sx]==1&&board[my][mx]==1&&board[y][x]==0){board[sy][sx]=board[my][mx]=0;board[y][x]=1;}}}else if(game==7){if(board[sy][sx]>0&&board[y][x]==board[sy][sx])board[sy][sx]=board[y][x]=0;}else if(game==3){if(board[y][x]==0&&legalReversi(y,x,1)){placeReversi(y,x,1);aiReversi();}}else if(game==1){if(board[sy][sx]<0&&board[y][x]==0&&Math.abs(x-sx)==1&&y==sy-1){board[y][x]=-1;board[sy][sx]=0;aiMove();}}else if(game==0){if(board[sy][sx]<0&&board[y][x]>=0){board[y][x]=board[sy][sx];board[sy][sx]=0;aiMove();}}refreshCells();status.setText(gameStatus());}
    private void refreshCells(){if(cells==null)return;for(int y=0;y<cells.length;y++)for(int x=0;x<cells[y].length;x++){cells[y][x].setText(cellText(y,x));cells[y][x].setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected==y*8+x?0xFF365D7D:PANEL));}}
    private void aiMove(){List<int[]>m=new ArrayList<>();for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(board[y][x]>0)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int ny=y+dy,nx=x+dx;if(ny>=0&&ny<8&&nx>=0&&nx<8&&board[ny][nx]<=0)m.add(new int[]{y,x,ny,nx});}if(!m.isEmpty()){int[]q=m.get(random.nextInt(m.size()));board[q[2]][q[3]]=board[q[0]][q[1]];board[q[0]][q[1]]=0;}}
    private void drop(int c){for(int y=5;y>=0;y--)if(board[y][c]==0){board[y][c]=1;if(win(1)){finished=true;status.setText("You win!");}else{int col=random.nextInt(7);for(int yy=5;yy>=0;yy--)if(board[yy][col]==0){board[yy][col]=2;break;}if(win(2)){finished=true;status.setText("CPU wins");}}refreshCells();return;}}
    private boolean win(int p){for(int y=0;y<6;y++)for(int x=0;x<7;x++)if(board[y][x]==p)for(int[]d:new int[][]{{0,1},{1,0},{1,1},{1,-1}}){int n=1;for(int k=1;k<4;k++){int yy=y+d[0]*k,xx=x+d[1]*k;if(yy<0||yy>=6||xx<0||xx>=7||board[yy][xx]!=p)break;n++;}if(n==4)return true;}return false;}
    private boolean legalReversi(int y,int x,int p){for(int[]d:new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}}){int yy=y+d[0],xx=x+d[1],n=0;while(yy>=0&&yy<8&&xx>=0&&xx<8&&board[yy][xx]==3-p){n++;yy+=d[0];xx+=d[1];}if(n>0&&yy>=0&&yy<8&&xx>=0&&xx<8&&board[yy][xx]==p)return true;}return false;}
    private void placeReversi(int y,int x,int p){board[y][x]=p;for(int[]d:new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}}){int yy=y+d[0],xx=x+d[1];List<int[]>line=new ArrayList<>();while(yy>=0&&yy<8&&xx>=0&&xx<8&&board[yy][xx]==3-p){line.add(new int[]{yy,xx});yy+=d[0];xx+=d[1];}if(yy>=0&&yy<8&&xx>=0&&xx<8&&board[yy][xx]==p)for(int[]q:line)board[q[0]][q[1]]=p;}}
    private void aiReversi(){List<int[]>m=new ArrayList<>();for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(board[y][x]==0&&legalReversi(y,x,2))m.add(new int[]{y,x});if(!m.isEmpty()){int[]q=m.get(random.nextInt(m.size()));placeReversi(q[0],q[1],2);}}
    private void flip(int y,int x){board[y][x]^=1;for(int[]d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int yy=y+d[0],xx=x+d[1];if(yy>=0&&yy<5&&xx>=0&&xx<5)board[yy][xx]^=1;}int n=0;for(int[]r:board)for(int v:r)n+=v;if(n==0){finished=true;status.setText("Solved!");}refreshCells();}
    private String wordAt(int a,int b){int y=a/8,x=a%8,ey=b/8,ex=b%8,dy=Integer.compare(ey,y),dx=Integer.compare(ex,x);if(y!=ey&&x!=ex&&Math.abs(ey-y)!=Math.abs(ex-x))return "";StringBuilder s=new StringBuilder();while(true){s.append((char)board[y][x]);if(y==ey&&x==ex)break;y+=dy;x+=dx;}return s.toString();}
    private void renderMastermind(){status.setText("Cycle each slot through 6 colours, then submit. 8 attempts.");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);Button[] slots=new Button[4];for(int i=0;i<4;i++){final int k=i;slots[i]=button("Slot "+(i+1),()->{guess[k]=(guess[k]+1)%6;slots[k].setText("Slot "+(k+1)+" · "+(guess[k]+1));});row.addView(slots[i],new LinearLayout.LayoutParams(dp(125),dp(70)));}boardHost.addView(row);boardHost.addView(button("Submit guess",()->{attempts++;int exact=0,near=0;boolean[]usedS=new boolean[4],usedG=new boolean[4];for(int i=0;i<4;i++)if(guess[i]==secret[i]){exact++;usedS[i]=usedG[i]=true;}for(int i=0;i<4;i++)if(!usedG[i])for(int j=0;j<4;j++)if(!usedS[j]&&guess[i]==secret[j]){near++;usedS[j]=true;break;}status.setText("Attempt "+attempts+"/8 · "+exact+" exact · "+near+" misplaced");if(exact==4){finished=true;status.setText("Code cracked!");}else if(attempts>=8){finished=true;status.setText("Out of attempts · Code: "+Arrays.toString(secret));}}));}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_BACK&&game>=0){showMenu();return true;}return super.onKeyDown(key,e);}
}
