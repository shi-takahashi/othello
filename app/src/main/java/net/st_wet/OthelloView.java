package net.st_wet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.st_wet.model.Board;
import net.st_wet.model.Cell;
import net.st_wet.model.Cell.E_STATUS;

import java.util.ArrayList;
import java.util.HashMap;

public class OthelloView extends View
{
    private Board mBoard = new Board();
    private Handler mHandler = new Handler();
    private Paint mPaint = new Paint();
    private boolean mLock = false;
    private E_STATUS mMyTurn = E_STATUS.None;
    private int mR = 0;
    private int mC = 0;
    private int mDepth = 3;
    private String mHistory = "";
    private boolean mbUseBack = false;  // 戻るボタンを使用して実際に戻ったかどうか
    private Cpu mCpu = null;

    public OthelloView(Context context, AttributeSet atr) {
        super(context, atr);

        setFocusable(true);

//        mMyTurn = E_STATUS.None;

        mCpu = new Cpu(E_STATUS.None);
        mCpu.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() <= 0) {
            return;
        }

        mBoard.setSize(getWidth(), getHeight());

        int bw = mBoard.getWidth();
        int bh = mBoard.getHeight();
        int cw = mBoard.getCellWidth();
        int ch = mBoard.getCellHeidht();

        mPaint.setAlpha(255);

        // 背景
        mPaint.setColor(Color.rgb(0, 180, 0));
        canvas.drawRect( 0, 0, bw, bh, mPaint);

        // 罫線
        mPaint.setColor(Color.rgb(40, 40, 40));
        for (int c = 0; c < Board.COLS; c++) {
            canvas.drawLine(cw * (c + 1), 0, cw * (c + 1), bh, mPaint);     // 縦線
        }
        for (int r = 0; r < Board.ROWS; r++) {
            canvas.drawLine(0, ch * (r + 1), bw, ch * (r + 1), mPaint);     // 横線
        }

        // 石
        mPaint.setAntiAlias(true);
        Cell[][] cells = mBoard.getCells();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Cell cell = cells[r][c];
                E_STATUS status = cell.getStatus();

                if (status == E_STATUS.None) {
                    continue;
                } else if (status == E_STATUS.Black){
                    mPaint.setColor(Color.BLACK);
                } else if(status == E_STATUS.White){
                    mPaint.setColor(Color.WHITE);
                }

                canvas.drawOval(cell.getStoneRectF(), mPaint);
            }
        }

        // 候補
        if (mBoard.getTurn() == mMyTurn && !mLock) {
            mPaint.setColor(Color.BLACK);
            mPaint.setAlpha(32);
            ArrayList<HashMap> list = mBoard.getCanPutRCs(mBoard.getTurn());
            for (HashMap<String, Integer> map : list) {
                int r = map.get("r");
                int c = map.get("c");
                Cell cell = cells[r][c];
                canvas.drawCircle(cell.getCx(), cell.getCy(),cw * 0.1f, mPaint);
            }
        }

        // 個数
//        TextView txt_stone = ((MainActivity)getContext()).findViewById(R.id.txtStone);
//        if (txt_stone != null) {
            String text = " 黒" + String.format("%2d", mBoard.getStatusCount(E_STATUS.Black))
                    + "  白" + String.format("%2d", mBoard.getStatusCount(E_STATUS.White));
//            txt_stone.setText(text);
            mPaint.setColor(Color.BLACK);
            mPaint.setTextSize(toDimensionTextSize(getContext(), 18.0f));
            float x = 0;
            float y = (Board.ROWS * mBoard.getCellHeidht()) + (mBoard.getCellHeidht() / 2f);
            canvas.drawText(text, x, y, mPaint);
//        }
    }

    /**
     * 端末に最適化したテキストサイズを取得
     *
     * @param context コンテキスト
     * @param size 元サイズ
     * @return 最適化されたテキストサイズ
     */
    public static float toDimensionTextSize(Context context, float size) {

        Resources r;
        if (context == null) {
            r = Resources.getSystem();

        } else {
            r = context.getResources();
        }
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, size, r.getDisplayMetrics());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mBoard.getTurn() != mMyTurn) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

         switch (event.getAction()) {
             case MotionEvent.ACTION_UP:
                 mR = (int)(y / mBoard.getCellHeidht());
                 mC = (int)(x / mBoard.getCellWidth());

                 if (!(mR < Board.ROWS && mC < Board.COLS)) {
                     break;
                 }

                 if (!mBoard.isCanPut(mBoard.getTurn(), mR, mC)) {
                     break;
                 }

                 if (mLock) {
                     return false;
                 }
                 mLock = true;

                 new Thread(new Runnable() {
                     @Override
                     public void run() {
                         putStone();
                         mLock = false;
                     }
                 }).start();

                 break;

             default:
                 break;
         }

        return true;
    }

    public void putStone() {
        mBoard.changeCell(mR, mC);

        mHistory += String.valueOf(mBoard.getTurn().ordinal());
        mHistory += String.valueOf(mR);
        mHistory += String.valueOf(mC);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });

        Thread turnOverThread = new TunrOver(mBoard.turnOverCells(mBoard.getTurn(), mR, mC, true));
        turnOverThread.start();
        try {
            turnOverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mBoard.countCell();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });

        if (!mBoard.isCanPutAll(mBoard.getOppositeTurn())) {
            if (!mBoard.isCanPutAll(mBoard.getTurn())) {
                gameSet();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showResult();
                    }
                });
            } else {
                // 相手が置く場所がない
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String text = Cell.STATUS_NAME[mBoard.getOppositeTurn().ordinal()] + "は置ける場所がありません。";
                        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            mBoard.changeTurn();
        }
    }

    public void restart() {
        mBoard.reset();
        mHistory = "";
        mbUseBack = false;
        invalidate();
    }

    public void gameSet() {
        mBoard.endTurn();
    }

    public void showResult() {
        int count_black = mBoard.getStatusCount(E_STATUS.Black);
        int count_white = mBoard.getStatusCount(E_STATUS.White);

        if (mbUseBack == false) {
            int level = (getDepth() + 1) / 2;
            int point = 0;
            if (mMyTurn == E_STATUS.Black) {
                point = count_black - count_white;
            } else {
                point = count_white - count_black;
            }
            ((MainActivity) getContext()).saveResult(level, point);
        }

        String message = "";
        if (mMyTurn == E_STATUS.Black) {
            if (count_black > count_white) {
                message = "勝ち";
            } else if (count_black < count_white) {
                message = "負け";
            } else {
                message = "引き分け";
            }
        } else {
            if (count_black < count_white) {
                message = "勝ち";
            } else if (count_black > count_white) {
                message = "負け";
            } else {
                message = "引き分け";
            }
        }
        message += System.lineSeparator();
        message += " (" + "黒 " + count_black + " vs " + "白 " + count_white + ") ";

        new AlertDialog.Builder(
                getContext())
                .setTitle("結果")
                .setMessage(message)
                .setPositiveButton("終了", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((MainActivity) getContext()).end();
                    }
                })
//                .setNegativeButton("NO", null)
                .setNeutralButton("もう一回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restart();
                    }
                }).show();
    }

    public E_STATUS getCurrentTurn() {
        return this.mBoard.getTurn();
    }

    public String getCellsStatus() {
        StringBuilder sb = new StringBuilder();

        Cell[][] cells = mBoard.getCells();

        for (int r = 0; r < mBoard.ROWS; r++) {
            for (int c = 0; c < mBoard.ROWS; c++) {
                sb.append(cells[r][c].statusToString());
            }
        }

        return sb.toString();
    }

    public String getHistory() {
        return this.mHistory;
    }

    public boolean getFirst() {
        return (this.mMyTurn == E_STATUS.Black);
    }

    public void setTurn(E_STATUS turn) {
        this.mMyTurn = turn;
        if (turn == E_STATUS.Black) {
            this.mCpu.setTurn(E_STATUS.White);
        } else {
            this.mCpu.setTurn(E_STATUS.Black);
        }
    }

    public boolean getUseBack() {
        return this.mbUseBack;
    }

    public void setUseBack(boolean bUseBack) {
        this.mbUseBack = bUseBack;
    }

    public void back() {
        E_STATUS[] statuses = E_STATUS.values();

        if (mHistory != "") {
            mbUseBack = true;
        }

        int i = 0;
        for (i = mHistory.length() - 3; i > 0; i = i - 3) {
            int turn = Integer.parseInt(mHistory.substring(i, i + 1));
            if (statuses[turn] == mMyTurn) {
                break;
            }
        }
        if (i > 0) {
            mHistory = mHistory.substring(0, i);
        } else {
            mHistory = "";
        }

        replay();
    }

    public void replay() {
        E_STATUS[] statuses = E_STATUS.values();

        mBoard.reset();

        if (mHistory.length() > 0) {
            for (int i = 0; i < mHistory.length(); i = i + 3) {
                int turn = Integer.parseInt(mHistory.substring(i, i + 1));
                mBoard.setTurn(statuses[turn]);
                int r = Integer.parseInt(mHistory.substring(i + 1, i + 2));
                int c = Integer.parseInt(mHistory.substring(i + 2, i + 3));
                mBoard.changeCell(r, c);
                mBoard.turnOverCells(mBoard.getTurn(), r, c, false);
            }
            mBoard.changeTurn();
        }

        mBoard.countCell();

        invalidate();
    }

    public void restore(int current_turn, String cellsStatus, String history) {
        E_STATUS[] statuses = E_STATUS.values();
        this.mBoard.setTurn(statuses[current_turn]);

        this.mHistory = history;

        Cell[][] cells = mBoard.getCells();

        int i = 0;
        for (int r = 0; r < mBoard.ROWS; r++) {
            for (int c = 0; c < mBoard.COLS; c++) {
                String s_sts = cellsStatus.substring(i, i + 1);
                int i_sts = Integer.parseInt(s_sts);
                E_STATUS e_sts = statuses[i_sts];
                cells[r][c].setStatus(e_sts);
                i++;
            }
        }

        mBoard.countCell();

        invalidate();
    }

    public int getDepth() {
        return this.mDepth;
    }

    public void setDepth(int depth) {
        this.mDepth = depth;
    }

    class Cpu extends Thread
    {
        public static final int UNDECIDED_SCORE = 99999;

        private E_STATUS my_turn;

        public Cpu(E_STATUS my_turn) {
            this.my_turn = my_turn;
        }

        public void setTurn(E_STATUS my_turn) {
            this.my_turn = my_turn;
        }
        public E_STATUS getTurn() {
            return this.my_turn;
        }

        @Override
        public void run() {
            while (true) {
                // 0.１秒毎にチェックする
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 自分のターンでなければ何もしない
                if (mBoard.getTurn() != this.my_turn) {
                    continue;
                }

                if (mLock) {
                    continue;
                }

                mLock = true;

                long start = System.currentTimeMillis();

                int score = alphaBeta(mBoard, mDepth, UNDECIDED_SCORE);
//                Log.d("search-alphaBeta", "score=" + score + ", mR=" + mR + ", mC=" + mC);

                // 考えている感。。
                while (true) {
                    if (System.currentTimeMillis() - start > 1000) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                putStone();

                mLock = false;
            }
        }

        private int alphaBeta(Board _board, int _depth, int _score) {
            int minimax_score = UNDECIDED_SCORE;

            ArrayList<HashMap> list = _board.getCanPutRCs(_board.getTurn());

            if (list.size() == 0) {
                Board board = _board.clone();

                int score = 0;

                if (_depth == 1) {
                    score = board.calcScore(board.getTurn());
                } else {
                    board.changeTurn();
                    score = alphaBeta(board, _depth - 1, minimax_score);
                }

//                Log.d("test", "depth=" + _depth + ", score=" + score);

                minimax_score = score;
            } else {
                for (HashMap<String, Integer> map : list) {
                    Board board = _board.clone();

                    int r = map.get("r");
                    int c = map.get("c");

                    board.changeCell(r, c);
                    board.turnOverCells(board.getTurn(), r, c, false);

                    int score = 0;

                    if (_depth == 1) {
                        score = board.calcScore(board.getTurn());
                    } else {
                        board.changeTurn();
                        score = alphaBeta(board, _depth - 1, minimax_score);
                    }

//                    Log.d("test", "depth=" + _depth + ", r=" + r + ", c=" + c + ", score=" + score);

                    if (minimax_score == UNDECIDED_SCORE) {
                        minimax_score = score;
                        if (_depth == mDepth) {
                            mR = r;
                            mC = c;
                        }
                    } else {
                        if (_depth % 2 == 1) {
                            if (minimax_score < score) {
                                minimax_score = score;
                                if (_depth == mDepth) {
                                    mR = r;
                                    mC = c;
                                }
                            }
                        } else {
                            if (minimax_score > score) {
                                minimax_score = score;
                                if (_depth == mDepth) {
                                    mR = r;
                                    mC = c;
                                }
                            }
                        }
                    }

                    if (_score != UNDECIDED_SCORE) {
                        if (_depth % 2 == 1) {
//                            if (_score < minimax_score) {
                            if (_score <= minimax_score) {
                                break;
                            }
                        } else {
//                            if (_score > minimax_score) {
                            if (_score >= minimax_score) {
                                break;
                            }
                        }
                    }
                }
            }

            return minimax_score;
        }

        private int minimax(Board _board, int _depth) {
            int best_score  = -999;
            int worst_score = 999;

            ArrayList<HashMap> list = _board.getCanPutRCs(_board.getTurn());

            if (list.size() == 0) {
                Board board = _board.clone();

                int score = 0;

                if (_depth == 1) {
                    score = board.calcScore(board.getTurn());
                } else {
                    board.changeTurn();
                    score = minimax(board, _depth - 1);
                }

//                Log.d("test", "depth=" + _depth + ", score=" + score);

                if (_depth % 2 == 1) {
                    best_score = score;
                } else {
                    worst_score = score;
                }
            } else {
                for (HashMap<String, Integer> map : list) {
                    Board board = _board.clone();

                    int r = map.get("r");
                    int c = map.get("c");

                    board.changeCell(r, c);
                    board.turnOverCells(board.getTurn(), r, c, false);

                    int score = 0;

                    if (_depth == 1) {
                        score = board.calcScore(board.getTurn());
                    } else {
                        board.changeTurn();
                        score = minimax(board, _depth - 1);
                    }

//                    Log.d("test", "depth=" + _depth + ", r=" + r + ", c=" + c + ", score=" + score);

                    if (_depth % 2 == 1) {
                        if (best_score < score) {
                            best_score = score;
                            if (_depth == mDepth) {
                                mR = r;
                                mC = c;
                            }
                        }
                    } else {
                        if (worst_score > score) {
                            worst_score = score;
                            if (_depth == mDepth) {
                                mR = r;
                                mC = c;
                            }
                        }
                    }
                }
            }

            if (_depth % 2 == 1) {
                return best_score;
            } else {
                return worst_score;
            }
        }
    }

    class TunrOver extends Thread
    {
        ArrayList<Cell> turnOverCellList;

        public TunrOver(ArrayList<Cell> turnOverCellList) {
            this.turnOverCellList = turnOverCellList;
        }

        @Override
        public void run() {
            try {
               Thread.sleep(100);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }

            for (float angle = 0; angle <= 90; angle = angle + 15) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (Cell cell : turnOverCellList) {
                    cell.setStatus(cell.getStatus(), angle);
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                });
            }

            for (Cell cell : turnOverCellList) {
                cell.setStatus(mBoard.getTurn());
            }

            for (float angle = 90; angle <= 0; angle = angle - 15) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (Cell cell : turnOverCellList) {
                    cell.setStatus(cell.getStatus(), angle);
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                });
            }
        }
    }
}
