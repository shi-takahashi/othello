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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;
            if ((row == 3 && col == 3) || (row == 4 && col == 4)) {
                sb.append("2");
            } else if ((row == 3 && col == 4) || (row == 4 && col == 3)) {
                sb.append("1");
            } else {
                sb.append("0");
            }
        }
        return sb.toString();
    }

    public void createRoom(OnRoomCreatedListener listener) {
        String roomCode = generateRoomCode();

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("board", getInitialBoard());
        gameData.put("currentTurn", "black");
        gameData.put("playerBlack", deviceId);
        gameData.put("playerWhite", null);
        gameData.put("status", "waiting");
        gameData.put("lastMoveRow", null);
        gameData.put("lastMoveCol", null);
        gameData.put("winner", null);
        gameData.put("createdAt", FieldValue.serverTimestamp());
        gameData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_GAMES).document(roomCode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        createRoom(listener);
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
