package net.st_wet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.SoundPool;
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
import java.util.Random;

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
    private boolean mShowLastMove = true;  // 直前の手を表示するかどうか
    private Cpu mCpu = null;
    private SoundPool mSoundPool;
    private int mSoundIdPut;
    private boolean mSoundEnabled = true;
    private boolean mSoundLoaded = false;
    private int mHandicapTarget = 0;  // 0=なし, 1=自分, 2=相手
    private int mHandicapCount = 1;   // 1〜4
    private boolean mRandomMode = false;  // ランダムモード
    private String mInitialBoardState = null;  // 初期盤面状態（待った用）
    private volatile int mGameVersion = 0;  // ゲームバージョン（リスタート検出用）

    // オンライン対戦用
    private boolean mOnlineMode = false;
    private E_STATUS mOnlineTurn = E_STATUS.None;  // オンラインでの自分の色
    private OnMoveListener mOnMoveListener = null;

    public interface OnMoveListener {
        void onMove(int row, int col, String boardState, String currentTurn, String passedPlayer);
    }

    public OthelloView(Context context, AttributeSet atr) {
        super(context, atr);

        setFocusable(true);

//        mMyTurn = E_STATUS.None;

        mCpu = new Cpu(E_STATUS.None);
        mCpu.start();

        // サウンド初期化
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();
        mSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                mSoundLoaded = true;
            }
        });
        mSoundIdPut = mSoundPool.load(context, R.raw.stone_put, 1);
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
        mPaint.setStrokeWidth(1);
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

        // 直前の手をマーキング
        if (mShowLastMove && mHistory.length() >= 3) {
            int lastIndex = mHistory.length() - 3;
            int lastR = Integer.parseInt(mHistory.substring(lastIndex + 1, lastIndex + 2));
            int lastC = Integer.parseInt(mHistory.substring(lastIndex + 2, lastIndex + 3));
            Cell lastCell = cells[lastR][lastC];
            mPaint.setColor(Color.RED);
            mPaint.setAlpha(255);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(4);
            canvas.drawCircle(lastCell.getCx(), lastCell.getCy(), cw * 0.15f, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
        }

        // 候補
        E_STATUS myTurnForDisplay = mOnlineMode ? mOnlineTurn : mMyTurn;
        if (mBoard.getTurn() == myTurnForDisplay && !mLock) {
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
        // オンラインモードの場合
        if (mOnlineMode) {
            if (mBoard.getTurn() != mOnlineTurn) {
                return false;  // 相手のターン中は操作不可
            }
        } else {
            if (mBoard.getTurn() != mMyTurn) {
                return false;
            }
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
        // 石を置く音を再生
        if (mSoundEnabled && mSoundLoaded) {
            mSoundPool.play(mSoundIdPut, 1.0f, 1.0f, 0, 0, 1.0f);
        }

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

        // オンラインモードの場合はコールバックを呼び出す
        if (mOnlineMode && mOnMoveListener != null) {
            String nextTurn = "black";
            boolean gameEnded = false;
            String passedPlayer = null;

            if (!mBoard.isCanPutAll(mBoard.getOppositeTurn())) {
                if (!mBoard.isCanPutAll(mBoard.getTurn())) {
                    gameEnded = true;
                    nextTurn = "none";
                } else {
                    // 相手がパスの場合は自分のターン継続（ターン変更しない）
                    passedPlayer = (mBoard.getOppositeTurn() == E_STATUS.Black) ? "black" : "white";
                    nextTurn = (mBoard.getTurn() == E_STATUS.Black) ? "black" : "white";
                }
            } else {
                mBoard.changeTurn();
                nextTurn = (mBoard.getTurn() == E_STATUS.Black) ? "black" : "white";
            }

            notifyScoreChanged();

            final int moveR = mR;
            final int moveC = mC;
            final String boardState = getCellsStatus();
            final String finalNextTurn = nextTurn;
            final boolean finalGameEnded = gameEnded;
            final String finalPassedPlayer = passedPlayer;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mOnMoveListener.onMove(moveR, moveC, boardState, finalNextTurn, finalPassedPlayer);
                    // 結果表示はOnlineGameActivityに任せる
                }
            });
            return;
        }

        if (!mBoard.isCanPutAll(mBoard.getOppositeTurn())) {
            if (!mBoard.isCanPutAll(mBoard.getTurn())) {
                gameSet();
                notifyScoreChanged();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showResult();
                    }
                });
            } else {
                // 相手が置く場所がない（パス）- ターンは変わらない
                notifyScoreChanged();
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
            notifyScoreChanged();
        }
    }

    public void restart() {
        // ゲームバージョンをインクリメント（CPUの古い思考結果を無効化）
        mGameVersion++;
        // ロックを解除（CPUの思考中でもリスタート後は操作可能にする）
        mLock = false;

        if (mRandomMode) {
            // ランダムモード：ランダムな配置で開始
            setupRandomBoard();
        } else {
            // 通常モード
            int actualHandicapTarget = mHandicapTarget;
            // プレイヤーが白の場合、ハンディキャップの対象を入れ替える
            // （Board.init()では1=黒、2=白として処理されるため）
            if (mHandicapTarget != 0 && mMyTurn == E_STATUS.White) {
                actualHandicapTarget = (mHandicapTarget == 1) ? 2 : 1;
            }
            mBoard.reset(actualHandicapTarget, mHandicapCount);
            mBoard.countCell();
            notifyScoreChanged();
        }
        // 初期盤面を保存（待った機能用）
        mInitialBoardState = getCellsStatus();
        mHistory = "";
        mbUseBack = false;
        invalidate();
    }

    /**
     * ランダムな初期配置を生成する
     * 通常の初期配置（中央4マス）に加えて、ランダムに石を追加配置する
     */
    private void setupRandomBoard() {
        Random random = new Random();
        final int MAX_ATTEMPTS = 10;  // 最大試行回数
        final int FAIR_THRESHOLD = 500;  // 公平とみなすスコア閾値
        final int MIN_PLAYER_MOVES = 2;  // プレイヤーの最低着手可能数

        int bestScore = Integer.MAX_VALUE;
        Cell[][] bestCells = null;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // 通常の初期配置でリセット（中央4マス）
            mBoard.reset();

            // 追加で配置する石の数（6〜16個）
            int additionalStones = random.nextInt(11) + 6;

            // 黒と白を均等に追加
            int blackCount = additionalStones / 2;
            int whiteCount = additionalStones - blackCount;

            // 中央4マスは使用済みとしてマーク
            ArrayList<int[]> usedPositions = new ArrayList<>();
            usedPositions.add(new int[]{3, 3});
            usedPositions.add(new int[]{3, 4});
            usedPositions.add(new int[]{4, 3});
            usedPositions.add(new int[]{4, 4});

            // 黒を追加配置
            for (int i = 0; i < blackCount; i++) {
                int[] pos = getRandomEmptyPosition(random, usedPositions);
                if (pos != null) {
                    mBoard.setCell(pos[0], pos[1], E_STATUS.Black);
                    usedPositions.add(pos);
                }
            }

            // 白を追加配置
            for (int i = 0; i < whiteCount; i++) {
                int[] pos = getRandomEmptyPosition(random, usedPositions);
                if (pos != null) {
                    mBoard.setCell(pos[0], pos[1], E_STATUS.White);
                    usedPositions.add(pos);
                }
            }

            // プレイヤーが複数箇所に打てる状態か確認
            int playerMoves = mBoard.getCanPutRCs(mMyTurn).size();

            if (playerMoves < MIN_PLAYER_MOVES) {
                // プレイヤーの選択肢が少ない場合は再試行
                continue;
            }

            // 局面評価（黒視点のスコア）
            int score = mCpu.evaluatePosition(mBoard, E_STATUS.Black);
            int absScore = Math.abs(score);

            // より公平な局面を記録
            if (absScore < bestScore) {
                bestScore = absScore;
                bestCells = copyCells(mBoard.getCells());
            }

            // 十分公平なら終了
            if (absScore < FAIR_THRESHOLD) {
                break;
            }
        }

        // 最も公平だった局面を適用
        if (bestCells != null) {
            applyCells(bestCells);
        }

        // 通常通り黒から開始
        mBoard.setTurn(E_STATUS.Black);

        mBoard.countCell();
        notifyScoreChanged();
    }

    /**
     * ランダムな空きマスを取得
     */
    private int[] getRandomEmptyPosition(Random random, ArrayList<int[]> usedPositions) {
        int maxAttempts = 100;
        for (int i = 0; i < maxAttempts; i++) {
            int r = random.nextInt(Board.ROWS);
            int c = random.nextInt(Board.COLS);

            boolean used = false;
            for (int[] pos : usedPositions) {
                if (pos[0] == r && pos[1] == c) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                return new int[]{r, c};
            }
        }
        return null;
    }

    /**
     * セルの状態をコピー
     */
    private Cell[][] copyCells(Cell[][] cells) {
        Cell[][] copy = new Cell[Board.ROWS][Board.COLS];
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                copy[r][c] = new Cell();
                copy[r][c].setStatus(cells[r][c].getStatus());
            }
        }
        return copy;
    }

    /**
     * コピーしたセルの状態を適用
     */
    private void applyCells(Cell[][] savedCells) {
        Cell[][] cells = mBoard.getCells();
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                cells[r][c].setStatus(savedCells[r][c].getStatus());
            }
        }
    }

    public void gameSet() {
        mBoard.endTurn();
    }

    public void showResult() {
        int count_black = mBoard.getStatusCount(E_STATUS.Black);
        int count_white = mBoard.getStatusCount(E_STATUS.White);

        int level = (getDepth() + 1) / 2;
        int point = 0;
        if (mMyTurn == E_STATUS.Black) {
            point = count_black - count_white;
        } else {
            point = count_white - count_black;
        }
        // 待ったを使った場合、ハンデを使った場合は成績に含めない
        boolean excludeFromStats = mbUseBack || isHandicapEnabled();
        ((MainActivity) getContext()).saveResult(level, point, excludeFromStats, mRandomMode);

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
                .setPositiveButton("閉じる", null)
                .setNeutralButton("成績確認", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((MainActivity) getContext()).result();
                    }
                })
                .setNegativeButton("もう一回", new DialogInterface.OnClickListener() {
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

    /**
     * ランダム盤面を生成して文字列として返す（オンライン対戦用）
     * CPU対戦と同じ公平性チェックを行う
     */
    public String generateRandomBoardString() {
        setupRandomBoard();
        return getCellsStatus();
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

    public void setShowLastMove(boolean showLastMove) {
        this.mShowLastMove = showLastMove;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.mSoundEnabled = soundEnabled;
    }

    public int getHandicapTarget() {
        return this.mHandicapTarget;
    }

    public int getHandicapCount() {
        return this.mHandicapCount;
    }

    public void setHandicap(int target, int count) {
        this.mHandicapTarget = target;
        this.mHandicapCount = count;
    }

    public boolean isHandicapEnabled() {
        return this.mHandicapTarget != 0;
    }

    public boolean isRandomMode() {
        return this.mRandomMode;
    }

    public void setRandomMode(boolean randomMode) {
        this.mRandomMode = randomMode;
    }

    // ===== スコア取得用メソッド =====

    public int getBlackCount() {
        return mBoard.getStatusCount(E_STATUS.Black);
    }

    public int getWhiteCount() {
        return mBoard.getStatusCount(E_STATUS.White);
    }

    public E_STATUS getMyTurn() {
        return mMyTurn;
    }

    // スコア変更リスナー
    public interface OnScoreChangeListener {
        void onScoreChanged(int blackCount, int whiteCount, E_STATUS currentTurn);
    }

    private OnScoreChangeListener mOnScoreChangeListener = null;

    public void setOnScoreChangeListener(OnScoreChangeListener listener) {
        this.mOnScoreChangeListener = listener;
    }

    private void notifyScoreChanged() {
        if (mOnScoreChangeListener != null) {
            int black = mBoard.getStatusCount(E_STATUS.Black);
            int white = mBoard.getStatusCount(E_STATUS.White);
            E_STATUS turn = mBoard.getTurn();
            mHandler.post(() -> mOnScoreChangeListener.onScoreChanged(black, white, turn));
        }
    }

    // ===== オンラインモード用メソッド =====

    public void setOnlineMode(boolean onlineMode) {
        this.mOnlineMode = onlineMode;
        if (onlineMode) {
            // オンラインモード時はCPUを無効化
            if (mCpu != null) {
                mCpu.setTurn(E_STATUS.None);
            }
        }
    }

    public boolean isOnlineMode() {
        return this.mOnlineMode;
    }

    public void setOnlineTurn(E_STATUS turn) {
        this.mOnlineTurn = turn;
    }

    public E_STATUS getOnlineTurn() {
        return this.mOnlineTurn;
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.mOnMoveListener = listener;
    }

    /**
     * オンライン対戦用：外部から石を置く（相手の手を反映）
     * @param row 行
     * @param col 列
     * @param nextTurn 次のターン ("black" or "white")
     */
    public void putStoneAt(int row, int col, String nextTurn) {
        mR = row;
        mC = col;

        if (!mBoard.isCanPut(mBoard.getTurn(), mR, mC)) {
            return;
        }

        mLock = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                putStoneOnline();

                // ターン変更
                if ("black".equals(nextTurn)) {
                    mBoard.setTurn(E_STATUS.Black);
                } else if ("white".equals(nextTurn)) {
                    mBoard.setTurn(E_STATUS.White);
                } else {
                    mBoard.setTurn(E_STATUS.None);
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                });

                mLock = false;
            }
        }).start();
    }

    /**
     * オンライン対戦用：石を置く処理（コールバックなし、盤面更新のみ）
     */
    private void putStoneOnline() {
        if (mSoundEnabled && mSoundLoaded) {
            mSoundPool.play(mSoundIdPut, 1.0f, 1.0f, 0, 0, 1.0f);
        }

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
        notifyScoreChanged();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

    /**
     * オンライン対戦用：盤面状態を適用
     */
    public void applyBoardState(String boardState, String currentTurn) {
        E_STATUS[] statuses = E_STATUS.values();

        // 現在のターンを設定
        if ("black".equals(currentTurn)) {
            mBoard.setTurn(E_STATUS.Black);
        } else if ("white".equals(currentTurn)) {
            mBoard.setTurn(E_STATUS.White);
        } else {
            mBoard.setTurn(E_STATUS.None);
        }

        // 盤面を適用
        Cell[][] cells = mBoard.getCells();
        int i = 0;
        for (int r = 0; r < mBoard.ROWS; r++) {
            for (int c = 0; c < mBoard.COLS; c++) {
                String s_sts = boardState.substring(i, i + 1);
                int i_sts = Integer.parseInt(s_sts);
                E_STATUS e_sts = statuses[i_sts];
                cells[r][c].setStatus(e_sts);
                i++;
            }
        }

        mBoard.countCell();
        notifyScoreChanged();
        invalidate();
    }

    /**
     * オンライン対戦用：ゲーム初期化
     */
    public void restartOnline(String myColor) {
        mOnlineMode = true;
        mOnlineTurn = "black".equals(myColor) ? E_STATUS.Black : E_STATUS.White;
        mBoard.reset();
        mHistory = "";
        mbUseBack = false;

        // CPUを無効化
        if (mCpu != null) {
            mCpu.setTurn(E_STATUS.None);
        }

        invalidate();
    }

    /**
     * オンライン対戦用：結果表示
     */
    public void showOnlineResult() {
        int count_black = mBoard.getStatusCount(E_STATUS.Black);
        int count_white = mBoard.getStatusCount(E_STATUS.White);

        String message = "";
        if (mOnlineTurn == E_STATUS.Black) {
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

        new AlertDialog.Builder(getContext())
                .setTitle("対戦結果")
                .setMessage(message)
                .setPositiveButton("閉じる", null)
                .show();
    }

    /**
     * オンライン対戦用：勝者を取得
     */
    public String getWinner() {
        int count_black = mBoard.getStatusCount(E_STATUS.Black);
        int count_white = mBoard.getStatusCount(E_STATUS.White);

        if (count_black > count_white) {
            return "black";
        } else if (count_black < count_white) {
            return "white";
        } else {
            return "draw";
        }
    }

    // ===== オンラインモード用メソッド ここまで =====

    public void back() {
        // 対局終了後は待ったを使えないようにする
        if (mBoard.getTurn() == E_STATUS.None) {
            return;
        }

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

        // 保存された初期盤面から復元（ランダムモード・ハンデ対応）
        if (mInitialBoardState != null) {
            Cell[][] cells = mBoard.getCells();
            int idx = 0;
            for (int r = 0; r < mBoard.ROWS; r++) {
                for (int c = 0; c < mBoard.COLS; c++) {
                    String s_sts = mInitialBoardState.substring(idx, idx + 1);
                    int i_sts = Integer.parseInt(s_sts);
                    cells[r][c].setStatus(statuses[i_sts]);
                    idx++;
                }
            }
            mBoard.setTurn(E_STATUS.Black);  // 初期ターンは黒
        } else {
            mBoard.reset();
        }

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
        notifyScoreChanged();

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
        notifyScoreChanged();

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
        public static final int INF = 100000000;

        // Lv.3用定数
        private static final int LV3_NORMAL_DEPTH = 7;           // 序盤・中盤の探索深さ
        private static final int LV3_ENDGAME_THRESHOLD = 12;     // 終盤完全読みの閾値（残り手数）
        private static final long LV3_TIME_LIMIT_MS = 8000;      // 思考時間上限（8秒）

        // Lv.3用評価関数の重み
        private static final int WEIGHT_POSITION = 10;           // 位置評価の重み
        private static final int WEIGHT_MOBILITY = 80;           // 着手可能数の重み
        private static final int WEIGHT_STABLE = 100;            // 安定石の重み
        private static final int WEIGHT_CORNER = 500;            // 角の重み
        private static final int WEIGHT_X_SQUARE = -150;         // X打ち（角の斜め隣）のペナルティ
        private static final int WEIGHT_C_SQUARE = -50;          // C打ち（角の隣）のペナルティ

        // Lv.3用：思考開始時刻とタイムアウトフラグ
        private long thinkStartTime;
        private volatile boolean isTimeout;

        // Lv.3用：反復深化で見つけた最善手を保存
        private int bestMoveR;
        private int bestMoveC;

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

        /**
         * 局面評価（公開メソッド）
         * ランダムモードの公平性チェック用
         */
        public int evaluatePosition(Board board, E_STATUS turn) {
            return evalPositionLv3(board, turn);
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

                // オンラインモードではCPUは動作しない
                if (mOnlineMode) {
                    continue;
                }

                // 自分のターンでなければ何もしない
                if (mBoard.getTurn() != this.my_turn) {
                    continue;
                }

                if (mLock) {
                    continue;
                }

                mLock = true;

                // 思考開始時のゲームバージョンを記憶
                int gameVersionAtStart = mGameVersion;

                long start = System.currentTimeMillis();

                if (mDepth == 1) {
                    // Lv.1: 一番多くひっくり返せる場所を選びがち（初心者っぽい戦略）
                    ArrayList<HashMap> list = mBoard.getCanPutRCs(this.my_turn);
                    if (list.size() > 0) {
                        Random random = new Random();
                        if (random.nextInt(100) < 70) {
                            // 70%: 一番多く取れる場所を選ぶ
                            int maxCount = -1;
                            for (HashMap<String, Integer> map : list) {
                                int r = map.get("r");
                                int c = map.get("c");
                                int count = mBoard.turnOverCells(this.my_turn, r, c, true).size();
                                if (count > maxCount) {
                                    maxCount = count;
                                    mR = r;
                                    mC = c;
                                }
                            }
                        } else {
                            // 30%: ランダムに選ぶ
                            int index = random.nextInt(list.size());
                            HashMap<String, Integer> map = list.get(index);
                            mR = map.get("r");
                            mC = map.get("c");
                        }
                    }
                } else if (mDepth == 5) {
                    // Lv.3: 超強化AI
                    thinkLv3(mBoard);
                } else {
                    // Lv.2: Alpha-Beta探索
                    int score = alphaBeta(mBoard, mDepth, UNDECIDED_SCORE);
                }

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

                // ゲームがリスタートされていたら、この思考結果は破棄
                if (gameVersionAtStart != mGameVersion) {
                    mLock = false;
                    continue;
                }

                putStone();

                mLock = false;
            }
        }

        /**
         * Lv.3専用の思考ルーチン
         * - 終盤完全読み
         * - 反復深化
         * - 改善された評価関数
         * - 時間制限付き探索
         */
        private void thinkLv3(Board board) {
            thinkStartTime = System.currentTimeMillis();
            isTimeout = false;

            ArrayList<HashMap> moves = board.getCanPutRCs(this.my_turn);
            if (moves.size() == 0) {
                return;
            }

            // 打てる手が1つだけならそれを選ぶ
            if (moves.size() == 1) {
                HashMap<String, Integer> map = moves.get(0);
                mR = map.get("r");
                mC = map.get("c");
                return;
            }

            // 空きマス数を計算
            int emptyCount = countEmpty(board);

            if (emptyCount <= LV3_ENDGAME_THRESHOLD) {
                // 終盤完全読み
                thinkEndgame(board, emptyCount);
            } else {
                // 反復深化探索
                thinkIterativeDeepening(board);
            }
        }

        /**
         * 終盤完全読み：石差を最大化する
         */
        private void thinkEndgame(Board board, int emptyCount) {
            bestMoveR = -1;
            bestMoveC = -1;

            int bestScore = -INF;
            ArrayList<HashMap> moves = board.getCanPutRCs(this.my_turn);

            // 手を並べ替え（角優先）
            sortMovesByPriority(moves);

            for (HashMap<String, Integer> map : moves) {
                if (checkTimeout()) break;

                int r = map.get("r");
                int c = map.get("c");

                Board newBoard = board.clone();
                newBoard.changeCell(r, c);
                newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
                newBoard.changeTurn();

                int score = -endgameSearch(newBoard, emptyCount - 1, -INF, -bestScore, this.my_turn);

                if (score > bestScore) {
                    bestScore = score;
                    bestMoveR = r;
                    bestMoveC = c;
                }
            }

            if (bestMoveR >= 0) {
                mR = bestMoveR;
                mC = bestMoveC;
            }
        }

        /**
         * 終盤探索（Negamax + Alpha-Beta）
         */
        private int endgameSearch(Board board, int depth, int alpha, int beta, E_STATUS originalTurn) {
            if (checkTimeout()) return 0;

            ArrayList<HashMap> moves = board.getCanPutRCs(board.getTurn());

            // ゲーム終了または深さ0
            if (depth == 0 || (moves.size() == 0 && !board.isCanPutAll(board.getOppositeTurn()))) {
                return evalFinalScore(board, originalTurn);
            }

            // パス
            if (moves.size() == 0) {
                board.changeTurn();
                return -endgameSearch(board, depth, -beta, -alpha, originalTurn);
            }

            // 手を並べ替え
            sortMovesByPriority(moves);

            int bestScore = -INF;

            for (HashMap<String, Integer> map : moves) {
                if (checkTimeout()) break;

                int r = map.get("r");
                int c = map.get("c");

                Board newBoard = board.clone();
                newBoard.changeCell(r, c);
                newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
                newBoard.changeTurn();

                int score = -endgameSearch(newBoard, depth - 1, -beta, -alpha, originalTurn);

                if (score > bestScore) {
                    bestScore = score;
                }
                if (score > alpha) {
                    alpha = score;
                }
                if (alpha >= beta) {
                    break; // Beta cutoff
                }
            }

            return bestScore;
        }

        /**
         * 最終石差を計算
         */
        private int evalFinalScore(Board board, E_STATUS originalTurn) {
            board.countCell();
            int myCount = board.getStatusCount(originalTurn);
            int oppCount = board.getStatusCount(Cell.getOppositeStatus(originalTurn));

            int diff = myCount - oppCount;

            // 現在の手番が元のプレイヤーかどうかで符号を調整
            if (board.getTurn() == originalTurn) {
                return diff * 100; // 石差を100倍してスコア化
            } else {
                return -diff * 100;
            }
        }

        /**
         * 反復深化探索
         */
        private void thinkIterativeDeepening(Board board) {
            bestMoveR = -1;
            bestMoveC = -1;

            ArrayList<HashMap> moves = board.getCanPutRCs(this.my_turn);

            // 最初の手を仮の最善手とする
            if (moves.size() > 0) {
                HashMap<String, Integer> firstMap = moves.get(0);
                bestMoveR = firstMap.get("r");
                bestMoveC = firstMap.get("c");
                mR = bestMoveR;
                mC = bestMoveC;
            }

            // 深さ1から開始して徐々に深くする
            for (int depth = 1; depth <= LV3_NORMAL_DEPTH; depth++) {
                if (checkTimeout()) break;

                int[] result = alphaBetaLv3(board, depth, -INF, INF, true, this.my_turn);

                if (!isTimeout && result[1] >= 0 && result[2] >= 0) {
                    bestMoveR = result[1];
                    bestMoveC = result[2];
                    mR = bestMoveR;
                    mC = bestMoveC;
                }
            }
        }

        /**
         * Lv.3用のAlpha-Beta探索（Negamax形式）
         * @return [スコア, 最善手のR, 最善手のC]
         */
        private int[] alphaBetaLv3(Board board, int depth, int alpha, int beta, boolean isRoot, E_STATUS originalTurn) {
            if (checkTimeout()) {
                return new int[]{0, -1, -1};
            }

            ArrayList<HashMap> moves = board.getCanPutRCs(board.getTurn());

            // ゲーム終了判定
            if (moves.size() == 0 && !board.isCanPutAll(board.getOppositeTurn())) {
                return new int[]{evalFinalScore(board, originalTurn), -1, -1};
            }

            // 深さ0で評価
            if (depth == 0) {
                int score = evalPositionLv3(board, originalTurn);
                // 現在の手番が元のプレイヤーでない場合は符号反転
                if (board.getTurn() != originalTurn) {
                    score = -score;
                }
                return new int[]{score, -1, -1};
            }

            // パス
            if (moves.size() == 0) {
                Board newBoard = board.clone();
                newBoard.changeTurn();
                int[] result = alphaBetaLv3(newBoard, depth, -beta, -alpha, false, originalTurn);
                return new int[]{-result[0], -1, -1};
            }

            // 手を並べ替え（角優先、X/C打ち回避）
            sortMovesByPriority(moves);

            int bestScore = -INF;
            int bestR = -1;
            int bestC = -1;

            for (HashMap<String, Integer> map : moves) {
                if (checkTimeout()) break;

                int r = map.get("r");
                int c = map.get("c");

                Board newBoard = board.clone();
                newBoard.changeCell(r, c);
                newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
                newBoard.changeTurn();

                int[] result = alphaBetaLv3(newBoard, depth - 1, -beta, -alpha, false, originalTurn);
                int score = -result[0];

                if (score > bestScore) {
                    bestScore = score;
                    bestR = r;
                    bestC = c;
                }
                if (score > alpha) {
                    alpha = score;
                }
                if (alpha >= beta) {
                    break; // Beta cutoff
                }
            }

            return new int[]{bestScore, bestR, bestC};
        }

        /**
         * Lv.3用の高度な評価関数
         */
        private int evalPositionLv3(Board board, E_STATUS myTurn) {
            E_STATUS oppTurn = Cell.getOppositeStatus(myTurn);
            Cell[][] cells = board.getCells();

            int score = 0;

            // 1. 位置評価（従来のスコア表）
            int positionScore = 0;
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    E_STATUS status = cells[r][c].getStatus();
                    if (status == myTurn) {
                        positionScore += Board.scores[r][c];
                    } else if (status == oppTurn) {
                        positionScore -= Board.scores[r][c];
                    }
                }
            }
            score += positionScore * WEIGHT_POSITION;

            // 2. 角の確保
            int cornerScore = 0;
            int[][] corners = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
            for (int[] corner : corners) {
                E_STATUS status = cells[corner[0]][corner[1]].getStatus();
                if (status == myTurn) {
                    cornerScore += WEIGHT_CORNER;
                } else if (status == oppTurn) {
                    cornerScore -= WEIGHT_CORNER;
                }
            }
            score += cornerScore;

            // 3. X打ち・C打ちのペナルティ（角が空いている場合のみ）
            int dangerScore = 0;
            // 左上角関連
            if (cells[0][0].getStatus() == E_STATUS.None) {
                dangerScore += evalDangerSquare(cells, 1, 1, myTurn, oppTurn, WEIGHT_X_SQUARE);
                dangerScore += evalDangerSquare(cells, 0, 1, myTurn, oppTurn, WEIGHT_C_SQUARE);
                dangerScore += evalDangerSquare(cells, 1, 0, myTurn, oppTurn, WEIGHT_C_SQUARE);
            }
            // 右上角関連
            if (cells[0][7].getStatus() == E_STATUS.None) {
                dangerScore += evalDangerSquare(cells, 1, 6, myTurn, oppTurn, WEIGHT_X_SQUARE);
                dangerScore += evalDangerSquare(cells, 0, 6, myTurn, oppTurn, WEIGHT_C_SQUARE);
                dangerScore += evalDangerSquare(cells, 1, 7, myTurn, oppTurn, WEIGHT_C_SQUARE);
            }
            // 左下角関連
            if (cells[7][0].getStatus() == E_STATUS.None) {
                dangerScore += evalDangerSquare(cells, 6, 1, myTurn, oppTurn, WEIGHT_X_SQUARE);
                dangerScore += evalDangerSquare(cells, 7, 1, myTurn, oppTurn, WEIGHT_C_SQUARE);
                dangerScore += evalDangerSquare(cells, 6, 0, myTurn, oppTurn, WEIGHT_C_SQUARE);
            }
            // 右下角関連
            if (cells[7][7].getStatus() == E_STATUS.None) {
                dangerScore += evalDangerSquare(cells, 6, 6, myTurn, oppTurn, WEIGHT_X_SQUARE);
                dangerScore += evalDangerSquare(cells, 7, 6, myTurn, oppTurn, WEIGHT_C_SQUARE);
                dangerScore += evalDangerSquare(cells, 6, 7, myTurn, oppTurn, WEIGHT_C_SQUARE);
            }
            score += dangerScore;

            // 4. 着手可能数（Mobility）
            int myMobility = board.getCanPutRCs(myTurn).size();
            int oppMobility = board.getCanPutRCs(oppTurn).size();
            score += (myMobility - oppMobility) * WEIGHT_MOBILITY;

            // 5. 安定石（確定石）の評価
            int stableScore = countStableDiscs(cells, myTurn) - countStableDiscs(cells, oppTurn);
            score += stableScore * WEIGHT_STABLE;

            return score;
        }

        /**
         * 危険マス（X打ち、C打ち）の評価
         */
        private int evalDangerSquare(Cell[][] cells, int r, int c, E_STATUS myTurn, E_STATUS oppTurn, int weight) {
            E_STATUS status = cells[r][c].getStatus();
            if (status == myTurn) {
                return weight; // 自分が置いている → ペナルティ
            } else if (status == oppTurn) {
                return -weight; // 相手が置いている → ボーナス
            }
            return 0;
        }

        /**
         * 安定石（もう返されない石）の数を数える
         * 簡易版：角からの連続した石をカウント
         */
        private int countStableDiscs(Cell[][] cells, E_STATUS turn) {
            int count = 0;

            // 4つの角からそれぞれ安定石を数える
            // 左上角から
            count += countStableFromCorner(cells, turn, 0, 0, 1, 1);
            // 右上角から
            count += countStableFromCorner(cells, turn, 0, 7, 1, -1);
            // 左下角から
            count += countStableFromCorner(cells, turn, 7, 0, -1, 1);
            // 右下角から
            count += countStableFromCorner(cells, turn, 7, 7, -1, -1);

            return count;
        }

        /**
         * 角から連続する安定石を数える
         */
        private int countStableFromCorner(Cell[][] cells, E_STATUS turn, int startR, int startC, int dr, int dc) {
            if (cells[startR][startC].getStatus() != turn) {
                return 0;
            }

            int count = 0;

            // 角から辺に沿って連続する石を数える
            // 縦方向
            int r = startR;
            while (r >= 0 && r < Board.ROWS && cells[r][startC].getStatus() == turn) {
                count++;
                r += dr;
            }

            // 横方向
            int c = startC + dc;
            while (c >= 0 && c < Board.COLS && cells[startR][c].getStatus() == turn) {
                count++;
                c += dc;
            }

            return count;
        }

        /**
         * 空きマス数を数える
         */
        private int countEmpty(Board board) {
            int count = 0;
            Cell[][] cells = board.getCells();
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    if (cells[r][c].getStatus() == E_STATUS.None) {
                        count++;
                    }
                }
            }
            return count;
        }

        /**
         * 手を優先度順に並べ替え（角優先、X/C打ち回避）
         */
        private void sortMovesByPriority(ArrayList<HashMap> moves) {
            moves.sort((a, b) -> {
                int ra = (int) a.get("r");
                int ca = (int) a.get("c");
                int rb = (int) b.get("r");
                int cb = (int) b.get("c");

                int prioA = getMovePriority(ra, ca);
                int prioB = getMovePriority(rb, cb);

                return prioB - prioA; // 高い優先度が先
            });
        }

        /**
         * 手の優先度を返す
         */
        private int getMovePriority(int r, int c) {
            // 角は最優先
            if ((r == 0 || r == 7) && (c == 0 || c == 7)) {
                return 100;
            }
            // 辺は次に優先
            if (r == 0 || r == 7 || c == 0 || c == 7) {
                // ただしC打ちは低優先
                if ((r == 0 || r == 7) && (c == 1 || c == 6)) return -10;
                if ((r == 1 || r == 6) && (c == 0 || c == 7)) return -10;
                return 50;
            }
            // X打ちは最低優先
            if ((r == 1 || r == 6) && (c == 1 || c == 6)) {
                return -50;
            }
            // その他は中程度
            return 0;
        }

        /**
         * タイムアウトチェック
         */
        private boolean checkTimeout() {
            if (System.currentTimeMillis() - thinkStartTime > LV3_TIME_LIMIT_MS) {
                isTimeout = true;
            }
            return isTimeout;
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
