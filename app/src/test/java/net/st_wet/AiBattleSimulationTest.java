package net.st_wet;

import net.st_wet.model.Board;
import net.st_wet.model.Cell;
import net.st_wet.model.Cell.E_STATUS;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * AI同士の対戦シミュレーションテスト
 * 各レベル間で対戦させて、強さの順序が正しいか検証する
 */
public class AiBattleSimulationTest {

    private static final int GAMES_PER_MATCHUP = 50;  // 各組み合わせの対戦回数

    // Lv.3用定数
    private static final int LV3_NORMAL_DEPTH = 7;
    private static final int LV3_ENDGAME_THRESHOLD = 12;
    private static final int INF = 100000000;
    private static final int WEIGHT_POSITION = 10;
    private static final int WEIGHT_MOBILITY = 80;
    private static final int WEIGHT_STABLE = 100;
    private static final int WEIGHT_CORNER = 500;
    private static final int WEIGHT_X_SQUARE = -150;
    private static final int WEIGHT_C_SQUARE = -50;

    // 次善手を選ぶ確率（%）
    private static final int LV2_SUBOPTIMAL_RATE = 10;
    private static final int LV3_SUBOPTIMAL_RATE = 5;

    private Random testRandom = new Random();

    @Test
    public void testAiStrengthOrder() {
        System.out.println("=== AI対戦シミュレーション ===\n");

        // Lv2 vs Lv3
        int[] result23 = runMatchup(2, 3, GAMES_PER_MATCHUP);
        System.out.println("Lv2 vs Lv3: Lv2勝利=" + result23[0] + ", Lv3勝利=" + result23[1] + ", 引分=" + result23[2]);
        double lv3WinRate = (double) result23[1] / GAMES_PER_MATCHUP * 100;
        System.out.println("  → Lv3勝率: " + String.format("%.1f", lv3WinRate) + "%\n");

        System.out.println("=== 結果まとめ ===");
        System.out.println("Lv3 vs Lv2 勝率: " + String.format("%.1f", lv3WinRate) + "%");

        // 結果確認のみ（アサーションなし）
        System.out.println("\n=== 判定 ===");
        System.out.println("Lv3 > Lv2: " + (result23[1] > result23[0] ? "OK" : "NG"));
    }

    /**
     * 2つのレベル間で対戦を行う
     * @return [level1の勝利数, level2の勝利数, 引き分け数]
     */
    private int[] runMatchup(int level1, int level2, int games) {
        int wins1 = 0;
        int wins2 = 0;
        int draws = 0;

        for (int i = 0; i < games; i++) {
            // 先手後手を交互に
            boolean level1IsBlack = (i % 2 == 0);
            int blackLevel = level1IsBlack ? level1 : level2;
            int whiteLevel = level1IsBlack ? level2 : level1;
            int result = playGame(blackLevel, whiteLevel);

            if (result > 0) {
                // 黒の勝利
                if (level1IsBlack) wins1++;
                else wins2++;
            } else if (result < 0) {
                // 白の勝利
                if (level1IsBlack) wins2++;
                else wins1++;
            } else {
                draws++;
            }
        }

        return new int[]{wins1, wins2, draws};
    }

    /**
     * 1ゲームをプレイ
     * @return 正=黒勝ち、負=白勝ち、0=引き分け
     */
    private int playGame(int blackLevel, int whiteLevel) {
        Board board = new Board();
        Random random = new Random();

        while (true) {
            E_STATUS currentTurn = board.getTurn();

            // 両者パスならゲーム終了
            if (!board.isCanPutAll(E_STATUS.Black) && !board.isCanPutAll(E_STATUS.White)) {
                break;
            }

            // 現在のプレイヤーがパスの場合
            if (!board.isCanPutAll(currentTurn)) {
                board.changeTurn();
                continue;
            }

            // AIの手を決定
            int level = (currentTurn == E_STATUS.Black) ? blackLevel : whiteLevel;
            int[] move = getAiMove(board, currentTurn, level, random);

            if (move == null) {
                board.changeTurn();
                continue;
            }

            // 手を実行
            board.changeCell(move[0], move[1]);
            board.turnOverCells(currentTurn, move[0], move[1], false);
            board.changeTurn();
        }

        board.countCell();
        int black = board.getStatusCount(E_STATUS.Black);
        int white = board.getStatusCount(E_STATUS.White);

        return black - white;
    }

    /**
     * AIの手を取得
     */
    private int[] getAiMove(Board board, E_STATUS turn, int level, Random random) {
        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        if (moves.size() == 0) return null;
        if (moves.size() == 1) {
            HashMap<String, Integer> map = moves.get(0);
            return new int[]{map.get("r"), map.get("c")};
        }

        int depth;
        switch (level) {
            case 1: depth = 1; break;
            case 2: depth = 3; break;
            case 3: depth = 5; break;
            default: depth = 3; break;
        }

        if (depth == 1) {
            // Lv1: 70%で最多獲得、30%ランダム
            if (random.nextInt(100) < 70) {
                int maxCount = -1;
                int bestR = -1, bestC = -1;
                for (HashMap<String, Integer> map : moves) {
                    int r = map.get("r");
                    int c = map.get("c");
                    int count = board.turnOverCells(turn, r, c, true).size();
                    if (count > maxCount) {
                        maxCount = count;
                        bestR = r;
                        bestC = c;
                    }
                }
                return new int[]{bestR, bestC};
            } else {
                HashMap<String, Integer> map = moves.get(random.nextInt(moves.size()));
                return new int[]{map.get("r"), map.get("c")};
            }
        } else if (depth == 5) {
            // Lv3: 高度なAI（ランダム要素付き）
            return getMoveLv3WithRandom(board, turn, random);
        } else {
            // Lv2: Alpha-Beta（ランダム要素付き）
            return getMoveAlphaBetaWithRandom(board, turn, depth, random);
        }
    }

    /**
     * Lv2用：Alpha-Beta探索（ランダム要素付き）
     */
    private int[] getMoveAlphaBetaWithRandom(Board board, E_STATUS turn, int maxDepth, Random random) {
        ArrayList<HashMap> moves = board.getCanPutRCs(turn);

        // 全ての手のスコアを計算
        ArrayList<int[]> moveScores = new ArrayList<>();
        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(turn, r, c, false);
            newBoard.changeTurn();

            int score = -alphaBetaSearch(newBoard, maxDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, turn);
            moveScores.add(new int[]{r, c, score});
        }

        return selectMoveWithRandom(moveScores, LV2_SUBOPTIMAL_RATE, random);
    }

    /**
     * Lv3用：高度なAI（ランダム要素付き）
     */
    private int[] getMoveLv3WithRandom(Board board, E_STATUS turn, Random random) {
        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        int emptyCount = countEmpty(board);

        // 全ての手のスコアを計算
        ArrayList<int[]> moveScores = new ArrayList<>();
        int searchDepth = (emptyCount <= LV3_ENDGAME_THRESHOLD) ? emptyCount : LV3_NORMAL_DEPTH;

        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(turn, r, c, false);
            newBoard.changeTurn();

            int score;
            if (emptyCount <= LV3_ENDGAME_THRESHOLD) {
                score = -endgameSearch(newBoard, emptyCount - 1, -INF, INF, turn);
            } else {
                int[] result = alphaBetaLv3(newBoard, searchDepth - 1, -INF, INF, turn);
                score = -result[0];
            }
            moveScores.add(new int[]{r, c, score});
        }

        return selectMoveWithRandom(moveScores, LV3_SUBOPTIMAL_RATE, random);
    }

    /**
     * 同スコアの手からランダムに選択、確率で次善手を選択
     */
    private int[] selectMoveWithRandom(ArrayList<int[]> moveScores, int suboptimalRate, Random random) {
        if (moveScores.size() == 0) return null;

        // スコアでソート（降順）
        moveScores.sort((a, b) -> b[2] - a[2]);

        // 最善手と同スコアの手をリストアップ
        int bestScore = moveScores.get(0)[2];
        ArrayList<int[]> bestMoves = new ArrayList<>();
        ArrayList<int[]> secondBestMoves = new ArrayList<>();
        int secondBestScore = Integer.MIN_VALUE;

        for (int[] ms : moveScores) {
            if (ms[2] == bestScore) {
                bestMoves.add(ms);
            } else if (ms[2] > secondBestScore) {
                secondBestScore = ms[2];
                secondBestMoves.clear();
                secondBestMoves.add(ms);
            } else if (ms[2] == secondBestScore) {
                secondBestMoves.add(ms);
            }
        }

        // 確率で次善手を選択（次善手がある場合のみ）
        int[] selectedMove;
        if (secondBestMoves.size() > 0 && random.nextInt(100) < suboptimalRate) {
            selectedMove = secondBestMoves.get(random.nextInt(secondBestMoves.size()));
        } else {
            selectedMove = bestMoves.get(random.nextInt(bestMoves.size()));
        }

        return new int[]{selectedMove[0], selectedMove[1]};
    }

    /**
     * Lv2用：Alpha-Beta探索
     */
    private int[] getMoveAlphaBeta(Board board, E_STATUS turn, int maxDepth) {
        int bestR = -1, bestC = -1;
        int bestScore = Integer.MIN_VALUE;

        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(turn, r, c, false);
            newBoard.changeTurn();

            int score = -alphaBetaSearch(newBoard, maxDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, turn);

            if (score > bestScore) {
                bestScore = score;
                bestR = r;
                bestC = c;
            }
        }

        return new int[]{bestR, bestC};
    }

    private int alphaBetaSearch(Board board, int depth, int alpha, int beta, E_STATUS originalTurn) {
        ArrayList<HashMap> moves = board.getCanPutRCs(board.getTurn());

        if (depth == 0 || (moves.size() == 0 && !board.isCanPutAll(board.getOppositeTurn()))) {
            return evaluateBoard(board, originalTurn);
        }

        if (moves.size() == 0) {
            Board newBoard = board.clone();
            newBoard.changeTurn();
            return -alphaBetaSearch(newBoard, depth, -beta, -alpha, originalTurn);
        }

        int bestScore = Integer.MIN_VALUE + 1;

        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
            newBoard.changeTurn();

            int score = -alphaBetaSearch(newBoard, depth - 1, -beta, -alpha, originalTurn);

            if (score > bestScore) {
                bestScore = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }

        return bestScore;
    }

    private int evaluateBoard(Board board, E_STATUS turn) {
        int score = 0;
        Cell[][] cells = board.getCells();
        E_STATUS opp = Cell.getOppositeStatus(turn);

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                E_STATUS status = cells[r][c].getStatus();
                if (status == turn) {
                    score += Board.scores[r][c];
                } else if (status == opp) {
                    score -= Board.scores[r][c];
                }
            }
        }

        // Mobility（着手可能数）
        int myMobility = board.getCanPutRCs(turn).size();
        int oppMobility = board.getCanPutRCs(opp).size();
        score += (myMobility - oppMobility) * 10;

        // 現在の手番による符号調整
        if (board.getTurn() != turn) {
            score = -score;
        }

        return score;
    }

    /**
     * Lv3用：高度なAI
     */
    private int[] getMoveLv3(Board board, E_STATUS turn) {
        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        if (moves.size() == 0) return null;
        if (moves.size() == 1) {
            HashMap<String, Integer> map = moves.get(0);
            return new int[]{map.get("r"), map.get("c")};
        }

        int emptyCount = countEmpty(board);

        if (emptyCount <= LV3_ENDGAME_THRESHOLD) {
            return thinkEndgameLv3(board, turn, emptyCount);
        } else {
            return thinkIterativeDeepening(board, turn);
        }
    }

    private int[] thinkEndgameLv3(Board board, E_STATUS turn, int emptyCount) {
        int bestR = -1, bestC = -1;
        int bestScore = -INF;

        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        sortMovesByPriority(moves);

        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(turn, r, c, false);
            newBoard.changeTurn();

            int score = -endgameSearch(newBoard, emptyCount - 1, -INF, -bestScore, turn);

            if (score > bestScore) {
                bestScore = score;
                bestR = r;
                bestC = c;
            }
        }

        return new int[]{bestR, bestC};
    }

    private int endgameSearch(Board board, int depth, int alpha, int beta, E_STATUS originalTurn) {
        ArrayList<HashMap> moves = board.getCanPutRCs(board.getTurn());

        if (depth == 0 || (moves.size() == 0 && !board.isCanPutAll(board.getOppositeTurn()))) {
            return evalFinalScore(board, originalTurn);
        }

        if (moves.size() == 0) {
            Board newBoard = board.clone();
            newBoard.changeTurn();
            return -endgameSearch(newBoard, depth, -beta, -alpha, originalTurn);
        }

        sortMovesByPriority(moves);
        int bestScore = -INF;

        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
            newBoard.changeTurn();

            int score = -endgameSearch(newBoard, depth - 1, -beta, -alpha, originalTurn);

            if (score > bestScore) bestScore = score;
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }

        return bestScore;
    }

    private int evalFinalScore(Board board, E_STATUS originalTurn) {
        board.countCell();
        int myCount = board.getStatusCount(originalTurn);
        int oppCount = board.getStatusCount(Cell.getOppositeStatus(originalTurn));
        int diff = myCount - oppCount;

        if (board.getTurn() == originalTurn) {
            return diff * 100;
        } else {
            return -diff * 100;
        }
    }

    private int[] thinkIterativeDeepening(Board board, E_STATUS turn) {
        int bestR = -1, bestC = -1;

        ArrayList<HashMap> moves = board.getCanPutRCs(turn);
        if (moves.size() > 0) {
            HashMap<String, Integer> firstMap = moves.get(0);
            bestR = firstMap.get("r");
            bestC = firstMap.get("c");
        }

        for (int depth = 1; depth <= LV3_NORMAL_DEPTH; depth++) {
            int[] result = alphaBetaLv3(board, depth, -INF, INF, turn);
            if (result[1] >= 0 && result[2] >= 0) {
                bestR = result[1];
                bestC = result[2];
            }
        }

        return new int[]{bestR, bestC};
    }

    private int[] alphaBetaLv3(Board board, int depth, int alpha, int beta, E_STATUS originalTurn) {
        ArrayList<HashMap> moves = board.getCanPutRCs(board.getTurn());

        if (moves.size() == 0 && !board.isCanPutAll(board.getOppositeTurn())) {
            return new int[]{evalFinalScore(board, originalTurn), -1, -1};
        }

        if (depth == 0) {
            int score = evalPositionLv3(board, originalTurn);
            if (board.getTurn() != originalTurn) score = -score;
            return new int[]{score, -1, -1};
        }

        if (moves.size() == 0) {
            Board newBoard = board.clone();
            newBoard.changeTurn();
            int[] result = alphaBetaLv3(newBoard, depth, -beta, -alpha, originalTurn);
            return new int[]{-result[0], -1, -1};
        }

        sortMovesByPriority(moves);

        int bestScore = -INF;
        int bestR = -1, bestC = -1;

        for (HashMap<String, Integer> map : moves) {
            int r = map.get("r");
            int c = map.get("c");

            Board newBoard = board.clone();
            newBoard.changeCell(r, c);
            newBoard.turnOverCells(newBoard.getTurn(), r, c, false);
            newBoard.changeTurn();

            int[] result = alphaBetaLv3(newBoard, depth - 1, -beta, -alpha, originalTurn);
            int score = -result[0];

            if (score > bestScore) {
                bestScore = score;
                bestR = r;
                bestC = c;
            }
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }

        return new int[]{bestScore, bestR, bestC};
    }

    private int evalPositionLv3(Board board, E_STATUS myTurn) {
        E_STATUS oppTurn = Cell.getOppositeStatus(myTurn);
        Cell[][] cells = board.getCells();

        int score = 0;

        // 位置評価
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

        // 角の確保
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

        // X打ち・C打ちのペナルティ
        int dangerScore = 0;
        if (cells[0][0].getStatus() == E_STATUS.None) {
            dangerScore += evalDangerSquare(cells, 1, 1, myTurn, oppTurn, WEIGHT_X_SQUARE);
            dangerScore += evalDangerSquare(cells, 0, 1, myTurn, oppTurn, WEIGHT_C_SQUARE);
            dangerScore += evalDangerSquare(cells, 1, 0, myTurn, oppTurn, WEIGHT_C_SQUARE);
        }
        if (cells[0][7].getStatus() == E_STATUS.None) {
            dangerScore += evalDangerSquare(cells, 1, 6, myTurn, oppTurn, WEIGHT_X_SQUARE);
            dangerScore += evalDangerSquare(cells, 0, 6, myTurn, oppTurn, WEIGHT_C_SQUARE);
            dangerScore += evalDangerSquare(cells, 1, 7, myTurn, oppTurn, WEIGHT_C_SQUARE);
        }
        if (cells[7][0].getStatus() == E_STATUS.None) {
            dangerScore += evalDangerSquare(cells, 6, 1, myTurn, oppTurn, WEIGHT_X_SQUARE);
            dangerScore += evalDangerSquare(cells, 7, 1, myTurn, oppTurn, WEIGHT_C_SQUARE);
            dangerScore += evalDangerSquare(cells, 6, 0, myTurn, oppTurn, WEIGHT_C_SQUARE);
        }
        if (cells[7][7].getStatus() == E_STATUS.None) {
            dangerScore += evalDangerSquare(cells, 6, 6, myTurn, oppTurn, WEIGHT_X_SQUARE);
            dangerScore += evalDangerSquare(cells, 7, 6, myTurn, oppTurn, WEIGHT_C_SQUARE);
            dangerScore += evalDangerSquare(cells, 6, 7, myTurn, oppTurn, WEIGHT_C_SQUARE);
        }
        score += dangerScore;

        // Mobility
        int myMobility = board.getCanPutRCs(myTurn).size();
        int oppMobility = board.getCanPutRCs(oppTurn).size();
        score += (myMobility - oppMobility) * WEIGHT_MOBILITY;

        // 安定石
        int stableScore = countStableDiscs(cells, myTurn) - countStableDiscs(cells, oppTurn);
        score += stableScore * WEIGHT_STABLE;

        return score;
    }

    private int evalDangerSquare(Cell[][] cells, int r, int c, E_STATUS myTurn, E_STATUS oppTurn, int weight) {
        E_STATUS status = cells[r][c].getStatus();
        if (status == myTurn) return weight;
        else if (status == oppTurn) return -weight;
        return 0;
    }

    private int countStableDiscs(Cell[][] cells, E_STATUS turn) {
        int count = 0;
        count += countStableFromCorner(cells, turn, 0, 0, 1, 1);
        count += countStableFromCorner(cells, turn, 0, 7, 1, -1);
        count += countStableFromCorner(cells, turn, 7, 0, -1, 1);
        count += countStableFromCorner(cells, turn, 7, 7, -1, -1);
        return count;
    }

    private int countStableFromCorner(Cell[][] cells, E_STATUS turn, int startR, int startC, int dr, int dc) {
        if (cells[startR][startC].getStatus() != turn) return 0;

        int count = 0;
        int r = startR;
        while (r >= 0 && r < Board.ROWS && cells[r][startC].getStatus() == turn) {
            count++;
            r += dr;
        }
        int c = startC + dc;
        while (c >= 0 && c < Board.COLS && cells[startR][c].getStatus() == turn) {
            count++;
            c += dc;
        }
        return count;
    }

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

    private void sortMovesByPriority(ArrayList<HashMap> moves) {
        moves.sort((a, b) -> {
            int ra = (int) a.get("r");
            int ca = (int) a.get("c");
            int rb = (int) b.get("r");
            int cb = (int) b.get("c");
            return getMovePriority(rb, cb) - getMovePriority(ra, ca);
        });
    }

    private int getMovePriority(int r, int c) {
        if ((r == 0 || r == 7) && (c == 0 || c == 7)) return 100;
        if (r == 0 || r == 7 || c == 0 || c == 7) {
            if ((r == 0 || r == 7) && (c == 1 || c == 6)) return -10;
            if ((r == 1 || r == 6) && (c == 0 || c == 7)) return -10;
            return 50;
        }
        if ((r == 1 || r == 6) && (c == 1 || c == 6)) return -50;
        return 0;
    }
}
