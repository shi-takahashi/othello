package net.st_wet.util;

import android.provider.Settings;
import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FirestoreGameManager {
    private static final String TAG = "FirestoreGameManager";
    private static final String COLLECTION_GAMES = "games";

    private final FirebaseFirestore db;
    private final String deviceId;
    private ListenerRegistration gameListener;
    private String currentRoomCode;

    public interface OnRoomCreatedListener {
        void onSuccess(String roomCode);
        void onFailure(String error);
    }

    public interface OnRoomJoinedListener {
        void onSuccess(String myColor);
        void onFailure(String error);
    }

    public interface OnGameStateChangedListener {
        void onGameStateChanged(GameState state);
        void onOpponentJoined();
        void onOpponentLeft();
        void onError(String error);
    }

    public static class GameState {
        public String board;
        public String currentTurn;
        public String playerBlack;
        public String playerWhite;
        public String status;
        public Integer lastMoveRow;
        public Integer lastMoveCol;
        public String winner;
        public String passedPlayer;  // パスしたプレイヤー ("black" or "white" or null)
        public int handicapTarget;   // 0=なし, 1=黒にハンデ, 2=白にハンデ
        public int handicapCount;    // 1-4
        public boolean isRandomMode;

        public static GameState fromDocument(DocumentSnapshot doc) {
            GameState state = new GameState();
            state.board = doc.getString("board");
            state.currentTurn = doc.getString("currentTurn");
            state.playerBlack = doc.getString("playerBlack");
            state.playerWhite = doc.getString("playerWhite");
            state.status = doc.getString("status");
            state.lastMoveRow = doc.getLong("lastMoveRow") != null ? doc.getLong("lastMoveRow").intValue() : null;
            state.lastMoveCol = doc.getLong("lastMoveCol") != null ? doc.getLong("lastMoveCol").intValue() : null;
            state.winner = doc.getString("winner");
            state.passedPlayer = doc.getString("passedPlayer");
            state.handicapTarget = doc.getLong("handicapTarget") != null ? doc.getLong("handicapTarget").intValue() : 0;
            state.handicapCount = doc.getLong("handicapCount") != null ? doc.getLong("handicapCount").intValue() : 1;
            state.isRandomMode = doc.getBoolean("isRandomMode") != null && doc.getBoolean("isRandomMode");
            return state;
        }
    }

    public FirestoreGameManager(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public String getDeviceId() {
        return deviceId;
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    private String getInitialBoard() {
        return getInitialBoardWithHandicap(0, 0);
    }

    /**
     * ハンディキャップ付きの初期盤面を生成
     * @param handicapTarget 0=なし, 1=黒にハンデ, 2=白にハンデ
     * @param handicapCount 角の数（1-4）
     */
    public static String getInitialBoardWithHandicap(int handicapTarget, int handicapCount) {
        // 初期盤面: 0=空, 1=黒, 2=白
        char[][] board = new char[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = '0';
            }
        }

        // 中央4マスの初期配置
        board[3][3] = '2';  // 白
        board[3][4] = '1';  // 黒
        board[4][3] = '1';  // 黒
        board[4][4] = '2';  // 白

        // ハンディキャップ（角）の配置
        if (handicapTarget != 0 && handicapCount > 0) {
            char handicapColor = (handicapTarget == 1) ? '1' : '2';  // 1=黒にハンデ, 2=白にハンデ
            int[][] corners = {{0, 0}, {7, 7}, {0, 7}, {7, 0}};  // 左上, 右下, 右上, 左下
            for (int i = 0; i < handicapCount && i < 4; i++) {
                board[corners[i][0]][corners[i][1]] = handicapColor;
            }
        }

        // 文字列に変換
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                sb.append(board[r][c]);
            }
        }
        return sb.toString();
    }

    public void createRoom(OnRoomCreatedListener listener) {
        createRoom(getInitialBoard(), 0, 0, false, listener);
    }

    /**
     * ハンディキャップ/ランダムモード対応のルーム作成
     * @param initialBoard 初期盤面文字列
     * @param handicapTarget 0=なし, 1=黒にハンデ, 2=白にハンデ
     * @param handicapCount 角の数（1-4）
     * @param isRandomMode ランダムモードかどうか
     */
    public void createRoom(String initialBoard, int handicapTarget, int handicapCount,
                           boolean isRandomMode, OnRoomCreatedListener listener) {
        String roomCode = generateRoomCode();

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("board", initialBoard);
        gameData.put("currentTurn", "black");
        gameData.put("playerBlack", deviceId);
        gameData.put("playerWhite", null);
        gameData.put("status", "waiting");
        gameData.put("lastMoveRow", null);
        gameData.put("lastMoveCol", null);
        gameData.put("winner", null);
        gameData.put("handicapTarget", handicapTarget);
        gameData.put("handicapCount", handicapCount);
        gameData.put("isRandomMode", isRandomMode);
        gameData.put("createdAt", FieldValue.serverTimestamp());
        gameData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_GAMES).document(roomCode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        createRoom(initialBoard, handicapTarget, handicapCount, isRandomMode, listener);
                    } else {
                        db.collection(COLLECTION_GAMES).document(roomCode)
                                .set(gameData)
                                .addOnSuccessListener(aVoid -> {
                                    currentRoomCode = roomCode;
                                    listener.onSuccess(roomCode);
                                })
                                .addOnFailureListener(e -> {
                                    listener.onFailure(e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    listener.onFailure(e.getMessage());
                });
    }

    public void joinRoom(String roomCode, OnRoomJoinedListener listener) {
        DocumentReference docRef = db.collection(COLLECTION_GAMES).document(roomCode.toUpperCase());

        docRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                listener.onFailure("ルームが見つかりません");
                return;
            }

            String status = doc.getString("status");
            if (!"waiting".equals(status)) {
                listener.onFailure("このルームは既に対戦中です");
                return;
            }

            String playerBlack = doc.getString("playerBlack");
            if (deviceId.equals(playerBlack)) {
                currentRoomCode = roomCode.toUpperCase();
                listener.onSuccess("black");
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("playerWhite", deviceId);
            updates.put("status", "playing");
            updates.put("updatedAt", FieldValue.serverTimestamp());

            docRef.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        currentRoomCode = roomCode.toUpperCase();
                        listener.onSuccess("white");
                    })
                    .addOnFailureListener(e -> {
                        listener.onFailure(e.getMessage());
                    });
        }).addOnFailureListener(e -> {
            listener.onFailure(e.getMessage());
        });
    }

    public void startListening(String roomCode, OnGameStateChangedListener listener) {
        stopListening();

        currentRoomCode = roomCode.toUpperCase();
        DocumentReference docRef = db.collection(COLLECTION_GAMES).document(currentRoomCode);

        final String[] previousStatus = {null};
        final String[] previousPlayerWhite = {null};

        gameListener = docRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error.getMessage());
                return;
            }

            if (snapshot == null || !snapshot.exists()) {
                listener.onOpponentLeft();
                return;
            }

            GameState state = GameState.fromDocument(snapshot);

            if ("waiting".equals(previousStatus[0]) && "playing".equals(state.status)) {
                listener.onOpponentJoined();
            }

            if (previousPlayerWhite[0] != null && state.playerWhite == null) {
                listener.onOpponentLeft();
            }

            previousStatus[0] = state.status;
            previousPlayerWhite[0] = state.playerWhite;

            listener.onGameStateChanged(state);
        });
    }

    public void stopListening() {
        if (gameListener != null) {
            gameListener.remove();
            gameListener = null;
        }
    }

    public void updateGameState(String board, String currentTurn, Integer lastMoveRow, Integer lastMoveCol,
                                 String status, String winner, String passedPlayer, OnUpdateListener listener) {
        if (currentRoomCode == null) {
            if (listener != null) listener.onFailure("ルームに接続していません");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("board", board);
        updates.put("currentTurn", currentTurn);
        updates.put("lastMoveRow", lastMoveRow);
        updates.put("lastMoveCol", lastMoveCol);
        updates.put("status", status);
        updates.put("winner", winner);
        updates.put("passedPlayer", passedPlayer);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_GAMES).document(currentRoomCode)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure(e.getMessage());
                });
    }

    public interface OnUpdateListener {
        void onSuccess();
        void onFailure(String error);
    }

    public void leaveRoom(Runnable onComplete) {
        if (currentRoomCode == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        stopListening();

        db.collection(COLLECTION_GAMES).document(currentRoomCode)
                .delete()
                .addOnCompleteListener(task -> {
                    currentRoomCode = null;
                    if (onComplete != null) onComplete.run();
                });
    }

    public String getCurrentRoomCode() {
        return currentRoomCode;
    }

    public boolean isMyTurn(GameState state) {
        if (state == null) return false;
        if (deviceId.equals(state.playerBlack)) {
            return "black".equals(state.currentTurn);
        } else if (deviceId.equals(state.playerWhite)) {
            return "white".equals(state.currentTurn);
        }
        return false;
    }

    public String getMyColor(GameState state) {
        if (state == null) return null;
        if (deviceId.equals(state.playerBlack)) {
            return "black";
        } else if (deviceId.equals(state.playerWhite)) {
            return "white";
        }
        return null;
    }
}
