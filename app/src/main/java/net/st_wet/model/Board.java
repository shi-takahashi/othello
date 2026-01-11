package net.st_wet.model;

import java.util.ArrayList;
import java.util.HashMap;

import net.st_wet.model.Cell.E_STATUS;

public class Board implements Cloneable
{
    public static final int COLS = 8;
    public static final int ROWS = 8;

    private static final int DIRECTION_MAX = 8;

    private static int[][] directions = {

      { 0, -1},  // DIRECTION_UP,
      {-1, -1},  // DIRECTION_UP_LEFT,
      {-1,  0},  // DIRECTION_LEFT,
      {-1,  1},  // DIRECTION_DOWN_LEFT,
      { 0,  1},  // DIRECTION_DOWN,
      { 1,  1},  // DIRECTION_DOWN_RIGHT,
      { 1,  0},  // DIRECTION_RIGHT,
      { 1, -1},  // DIRECTION_UP_RIGHT,
    };

    public static final int[][] scores = {
            {120, -20, 20,  5,  5, 20, -20, 120},
            {-20, -40, -5, -5, -5, -5, -40, -20},
            { 20,  -5, 15,  3,  3, 15,  -5,  20},
            {  5,  -5,  3,  3,  3,  3,  -5,   5},
            {  5,  -5,  3,  3,  3,  3,  -5,   5},
            { 20,  -5, 15,  3,  3, 15,  -5,  20},
            {-20, -40, -5, -5, -5, -5, -40, -20},
            {120, -20, 20,  5,  5, 20, -20, 120},
    };
//    public static final int[][] scores = {
//        { 30, -12,  0, -1, -1,  0, -12,  30},
//        {-12, -15, -3, -3, -3, -3, -15, -12},
//        {  0,  -3,  0, -1, -1,  0,  -3,   0},
//        { -1,  -3, -1, -1, -1, -1,  -3,  -1},
//        { -1,  -3, -1, -1, -1, -1,  -3,  -1},
//        {  0,  -3,  0, -1, -1,  0,  -3,   0},
//        {-12, -15, -3, -3, -3, -3, -15, -12},
//        { 30, -12,  0, -1, -1,  0, -12,  30},
//    };

    private int width  = 0;
    private int height = 0;
    private int top    = 0;
    private int left   = 0;

    private Cell cells[][] = new Cell[ROWS][COLS];
    private Cell.E_STATUS turn;

    private int[] status_count = new int[3];

    private ArrayList<Cell> turn_overed_cells = new ArrayList<Cell>();

    @Override
    public Board clone() {
        Board clonedBoard = null;
        try {
            clonedBoard = (Board)super.clone();

            Cell cells[][] = new Cell[ROWS][COLS];
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    cells[r][c] = this.cells[r][c].clone();
                }
            }
            clonedBoard.cells = cells;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return clonedBoard;
    }

    public Board() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells[r][c] = new Cell();
            }
        }
        init();
    }

    public void reset() {
        reset(0, 1);
    }

    public void reset(int handicapTarget, int handicapCount) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells[r][c].setStatus(E_STATUS.None);
            }
        }
        init(handicapTarget, handicapCount);
    }

    /**
     * 盤面を空にする（石を一切置かない状態）
     */
    public void resetEmpty() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells[r][c].setStatus(E_STATUS.None);
            }
        }
        this.turn = E_STATUS.Black;
    }

    /**
     * 指定位置に石を配置
     */
    public void setCell(int r, int c, E_STATUS status) {
        cells[r][c].setStatus(status);
    }

    public void init() {
        init(0, 1);
    }

    /**
     * ハンディキャップ付きで初期化
     * @param handicapTarget 0=なし, 1=プレイヤー(黒), 2=相手(白)
     * @param handicapCount 角の数 (1〜4)
     */
    public void init(int handicapTarget, int handicapCount) {
        cells[ROWS/2 -1][COLS/2 -1].setStatus(E_STATUS.Black);
        cells[ROWS/2 -1][COLS/2   ].setStatus(E_STATUS.White);
        cells[ROWS/2   ][COLS/2 -1].setStatus(E_STATUS.White);
        cells[ROWS/2   ][COLS/2   ].setStatus(E_STATUS.Black);

        // ハンディキャップの角を配置
        if (handicapTarget != 0 && handicapCount >= 1) {
            E_STATUS handicapColor = (handicapTarget == 1) ? E_STATUS.Black : E_STATUS.White;

            // 角の位置: 左上, 右下, 右上, 左下の順
            int[][] corners = {{0, 0}, {ROWS-1, COLS-1}, {0, COLS-1}, {ROWS-1, 0}};

            for (int i = 0; i < handicapCount && i < 4; i++) {
                cells[corners[i][0]][corners[i][1]].setStatus(handicapColor);
            }
        }

        countCell();

        this.turn = E_STATUS.Black;
    }

    public void setSize(int width, int height) {
        int sz = width < height ? width : height;

        // 列数で割り切れない場合は余りを捨てる。
        this.width  = sz / Board.COLS * Board.COLS;
        this.height = sz / Board.ROWS * Board.ROWS;

        int cellW = this.getCellWidth();
        int cellH = this.getCellHeidht();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells[r][c].setWidth(cellW);
                cells[r][c].setLeft(c * cellW);
                cells[r][c].setHeight(cellH);
                cells[r][c].setTop(r * cellH);
            }
        }

    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public Cell[][] getCells() {
        return this.cells;
    }

    public int getCellWidth() {
        return this.width / COLS;
    }

    public int getCellHeidht() {
        return this.height / ROWS;
    }

    public E_STATUS getTurn() {
        return this.turn;
    }

    public E_STATUS getOppositeTurn() {
        return Cell.getOppositeStatus(getTurn());
    }

    public void changeTurn() {
        this.turn = getOppositeTurn();
    }

    public void endTurn() {
        this.turn = E_STATUS.None;
    }

    public void setTurn(E_STATUS turn) {
        this.turn = turn;
    }

    public ArrayList<Cell> turnOverCells(E_STATUS status, int r, int c, boolean dry_run) {
        ArrayList<Cell> turn_overed_cells = new ArrayList<Cell>();

        turn_overed_cells.clear();

        for (int i = 0; i < DIRECTION_MAX; i++) {
            int _r = r;
            int _c = c;

            // １つ隣に相手の石が置かれているか
            _r += directions[i][1];
            _c += directions[i][0];
            if ((_r < 0) || (_r >= ROWS) || (_c < 0) || (_c >= COLS)) {
                continue;
            }
            if (this.cells[_r][_c].getStatus() != Cell.getOppositeStatus(status)) {
                continue;
            }

            // ２マス以上離れた場所をチェック
            while (true) {
                _r += directions[i][1];
                _c += directions[i][0];
                // 板を全て調べても挟めなかった
                if ((_r < 0) || (_r >= ROWS) || (_c < 0) || (_c >= COLS)) {
                    break;
                }
                //　何も石が置かれていなければ挟めないのが確定
                if (this.cells[_r][_c].getStatus() == E_STATUS.None) {
                    break;
                }
                // 自分の石があったら挟めるのが確定
                if (this.cells[_r][_c].getStatus() == status) {
                    // 挟める石を抽出する
                    int r2 = r;
                    int c2 = c;
                    while (true) {
                        r2 += directions[i][1];
                        c2 += directions[i][0];
                        if (r2 == _r && c2 == _c) {
                            break;
                        }
                        if (!dry_run) {
                            this.cells[r2][c2].setStatus(status);
                        }
                        turn_overed_cells.add(this.cells[r2][c2]);
                    }
                    break;
                }
            }
        }

        return turn_overed_cells;
    }

    public boolean isCanPut(E_STATUS status, int r, int c) {
        if (this.cells[r][c].getStatus() != E_STATUS.None) {
            return false;
        }
        ArrayList<Cell> turn_overed_cells = turnOverCells(status, r, c, true);
        return (turn_overed_cells.size() > 0);
    }

    public ArrayList<HashMap> getCanPutRCs(E_STATUS status) {
        ArrayList<HashMap> list = new ArrayList<HashMap>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!isCanPut(status, r, c)) {
                    continue;
                }
                HashMap map = new HashMap<String, Integer>();
                map.put("r", r);
                map.put("c", c);
                list.add(map);
            }
        }
        return list;
    }

    public boolean isCanPutAll(E_STATUS status) {
        ArrayList<HashMap> list = getCanPutRCs(status);
        return (list.size() > 0);
    }

    public void changeCell(int r, int c) {
        if (isCanPut(getTurn(), r, c)) {
            this.cells[r][c].setStatus(getTurn());
        }
    }

    public int countStatusCell(E_STATUS status) {
        int count = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c].getStatus() != status) {
                    continue;
                }
                count++;
            }
        }
        this.status_count[status.ordinal()] = count;
        return count;
    }

    public void countCell() {
        countStatusCell(E_STATUS.Black);
        countStatusCell(E_STATUS.White);
    }

    public int getStatusCount(Cell.E_STATUS status) {
        return this.status_count[status.ordinal()];
    }

    public int calcScore(E_STATUS status) {
        int score = 0;
        // 位置による評価
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c].getStatus() != status) {
                    continue;
                }
                score += this.scores[r][c];
            }
        }

        // 着手可能数（Mobility）による評価
        E_STATUS oppStatus = Cell.getOppositeStatus(status);
        int myMobility = getCanPutRCs(status).size();
        int oppMobility = getCanPutRCs(oppStatus).size();
        score += (myMobility - oppMobility) * 20;

        return score;
    }
}
