package net.st_wet;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.button.MaterialButton;

import net.st_wet.model.Cell.E_STATUS;
import net.st_wet.util.FirestoreGameManager;

public class OnlineGameActivity extends AppCompatActivity {

    private static final String TAG = "OnlineGameActivity";

    private FirestoreGameManager gameManager;
    private OthelloView othelloView;

    private TextView txtRoomCode;
    private TextView txtMyColor;
    private TextView txtOpponentColor;
    private TextView txtMyCount;
    private TextView txtOpponentCount;
    private TextView txtTurnStatus;
    private LinearLayout layoutMyInfo;
    private LinearLayout layoutOpponentInfo;
    private MaterialButton btnLeave;
    private AdView adView;

    private String roomCode;
    private String myColor;
    private boolean gameEnded = false;
    private Integer lastAppliedMoveRow = null;
    private Integer lastAppliedMoveCol = null;
    private String lastPassedPlayer = null;  // 前回のパス状態を記録

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // アクションバーを非表示
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_online_game);

        roomCode = getIntent().getStringExtra("roomCode");
        myColor = getIntent().getStringExtra("myColor");

        if (roomCode == null || myColor == null) {
            Toast.makeText(this, "エラーが発生しました", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        gameManager = new FirestoreGameManager(this);

        initViews();
        setupGame();
        startListening();
    }

    private void initViews() {
        othelloView = findViewById(R.id.othelloView);
        txtRoomCode = findViewById(R.id.txtRoomCode);
        txtMyColor = findViewById(R.id.txtMyColor);
        txtOpponentColor = findViewById(R.id.txtOpponentColor);
        txtMyCount = findViewById(R.id.txtMyCount);
        txtOpponentCount = findViewById(R.id.txtOpponentCount);
        txtTurnStatus = findViewById(R.id.txtTurnStatus);
        layoutMyInfo = findViewById(R.id.layoutMyInfo);
        layoutOpponentInfo = findViewById(R.id.layoutOpponentInfo);
        btnLeave = findViewById(R.id.btnLeave);

        txtRoomCode.setText("Room: " + roomCode);

        btnLeave.setOnClickListener(v -> showLeaveConfirmation());

        // AdMob初期化
        MobileAds.initialize(this, initializationStatus -> {});
        adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void setupGame() {
        // OthelloViewをオンラインモードに設定
        othelloView.restartOnline(myColor);

        // 自分が石を置いた時のコールバック
        othelloView.setOnMoveListener((row, col, boardState, currentTurn, passedPlayer) -> {
            // Firestoreに盤面を更新
            String status = "none".equals(currentTurn) ? "finished" : "playing";
            String winner = "none".equals(currentTurn) ? othelloView.getWinner() : null;

            // パス通知を先に表示し、lastPassedPlayerを更新（Firestoreリスナーとの競合を防ぐ）
            if (passedPlayer != null) {
                String passedName = passedPlayer.equals(myColor) ? "あなた" : "相手";
                Toast.makeText(OnlineGameActivity.this,
                        passedName + "は置ける場所がありません", Toast.LENGTH_SHORT).show();
            }
            lastPassedPlayer = passedPlayer;

            gameManager.updateGameState(boardState, currentTurn, row, col, status, winner, passedPlayer,
                    new FirestoreGameManager.OnUpdateListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Game state updated successfully");
                        }

                        @Override
                        public void onFailure(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(OnlineGameActivity.this,
                                        "同期エラー: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        });

        // UI更新
        if ("black".equals(myColor)) {
            txtMyColor.setText("あなた (黒)");
            txtOpponentColor.setText("相手 (白)");
        } else {
            txtMyColor.setText("あなた (白)");
            txtOpponentColor.setText("相手 (黒)");
        }

        updateTurnDisplay("black");
    }

    private void startListening() {
        gameManager.startListening(roomCode, new FirestoreGameManager.OnGameStateChangedListener() {
            @Override
            public void onGameStateChanged(FirestoreGameManager.GameState state) {
                runOnUiThread(() -> handleGameStateChange(state));
            }

            @Override
            public void onOpponentJoined() {
                // Already in game
            }

            @Override
            public void onOpponentLeft() {
                runOnUiThread(() -> {
                    if (!gameEnded) {
                        showOpponentLeftDialog();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(OnlineGameActivity.this,
                            "エラー: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleGameStateChange(FirestoreGameManager.GameState state) {
        if (state == null) return;

        Log.d(TAG, "handleGameStateChange: status=" + state.status + ", currentTurn=" + state.currentTurn
                + ", winner=" + state.winner + ", gameEnded=" + gameEnded);

        // ゲーム終了判定を先に行う（確実に実行されるように）
        if ("finished".equals(state.status) && !gameEnded) {
            gameEnded = true;
            Log.d(TAG, "Game finished! winner=" + state.winner);
            // これ以上の状態更新を受け取らないようにリスナーを停止
            gameManager.stopListening();

            // 相手の最後の手を反映してから結果を表示
            if (state.lastMoveRow != null && state.lastMoveCol != null) {
                boolean isMyMove = myColor.equals(getLastMoveTurn(state));
                if (!isMyMove && !isSameMove(state.lastMoveRow, state.lastMoveCol)) {
                    othelloView.putStoneAt(state.lastMoveRow, state.lastMoveCol, state.currentTurn);
                    lastAppliedMoveRow = state.lastMoveRow;
                    lastAppliedMoveCol = state.lastMoveCol;
                }
            }

            // UI更新
            updateTurnDisplay(state.currentTurn);
            updateScoreDisplay(state.board);

            // 少し遅延させてアニメーション後に結果表示
            othelloView.postDelayed(() -> showGameResult(state.winner), 1500);
            return;
        }

        boolean opponentMoved = false;

        // 相手の手を反映（自分の手でない場合のみ）
        if (state.lastMoveRow != null && state.lastMoveCol != null) {
            boolean isMyMove = myColor.equals(getLastMoveTurn(state));

            if (!isMyMove && !isSameMove(state.lastMoveRow, state.lastMoveCol)) {
                // 相手の手をアニメーション付きで反映
                othelloView.putStoneAt(state.lastMoveRow, state.lastMoveCol, state.currentTurn);
                lastAppliedMoveRow = state.lastMoveRow;
                lastAppliedMoveCol = state.lastMoveCol;
                opponentMoved = true;
            }
        }

        // 相手が手を打った場合はアニメーション中なので盤面を上書きしない
        // 自分の手や初期状態の場合のみ盤面を同期
        if (!opponentMoved && state.currentTurn != null && !"none".equals(state.currentTurn)) {
            othelloView.applyBoardState(state.board, state.currentTurn);
        }

        // パス通知を表示（passedPlayerが新しく設定された場合）
        if (state.passedPlayer != null && !state.passedPlayer.equals(lastPassedPlayer)) {
            String passedName = state.passedPlayer.equals(myColor) ? "あなた" : "相手";
            Toast.makeText(this, passedName + "は置ける場所がありません", Toast.LENGTH_SHORT).show();
        }
        lastPassedPlayer = state.passedPlayer;

        // UI更新
        updateTurnDisplay(state.currentTurn);
        updateScoreDisplay(state.board);
    }

    private String getLastMoveTurn(FirestoreGameManager.GameState state) {
        // 最後に石を置いたプレイヤーを判定
        // currentTurnは「次のプレイヤー」なので、逆を返す
        if ("black".equals(state.currentTurn)) {
            return "white";
        } else if ("white".equals(state.currentTurn)) {
            return "black";
        }
        return null;
    }

    private boolean isSameMove(int row, int col) {
        return lastAppliedMoveRow != null && lastAppliedMoveCol != null
                && lastAppliedMoveRow == row && lastAppliedMoveCol == col;
    }

    private void updateTurnDisplay(String currentTurn) {
        boolean isMyTurn = myColor.equals(currentTurn);

        if ("none".equals(currentTurn) || currentTurn == null) {
            txtTurnStatus.setText("ゲーム終了");
            layoutMyInfo.setBackgroundResource(R.drawable.bg_player_inactive);
            layoutOpponentInfo.setBackgroundResource(R.drawable.bg_player_inactive);
        } else if (isMyTurn) {
            txtTurnStatus.setText("あなたのターンです");
            layoutMyInfo.setBackgroundResource(R.drawable.bg_player_active);
            layoutOpponentInfo.setBackgroundResource(R.drawable.bg_player_inactive);
        } else {
            txtTurnStatus.setText("相手のターンです");
            layoutMyInfo.setBackgroundResource(R.drawable.bg_player_inactive);
            layoutOpponentInfo.setBackgroundResource(R.drawable.bg_player_active);
        }
    }

    private void updateScoreDisplay(String board) {
        if (board == null || board.length() != 64) return;

        int blackCount = 0;
        int whiteCount = 0;

        for (int i = 0; i < 64; i++) {
            char c = board.charAt(i);
            if (c == '1') blackCount++;
            else if (c == '2') whiteCount++;
        }

        if ("black".equals(myColor)) {
            txtMyCount.setText(String.valueOf(blackCount));
            txtOpponentCount.setText(String.valueOf(whiteCount));
        } else {
            txtMyCount.setText(String.valueOf(whiteCount));
            txtOpponentCount.setText(String.valueOf(blackCount));
        }
    }

    private void showGameResult(String winner) {
        String message;
        if ("draw".equals(winner)) {
            message = "引き分けです";
        } else if (myColor.equals(winner)) {
            message = "勝ちました!";
        } else {
            message = "負けました...";
        }

        new AlertDialog.Builder(this)
                .setTitle("ゲーム終了")
                .setMessage(message)
                .setPositiveButton("閉じる", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showOpponentLeftDialog() {
        new AlertDialog.Builder(this)
                .setTitle("対戦終了")
                .setMessage("相手が退出しました")
                .setPositiveButton("閉じる", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("退出確認")
                .setMessage("対戦を終了しますか？")
                .setPositiveButton("退出", (dialog, which) -> leaveGame())
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void leaveGame() {
        gameManager.stopListening();
        gameManager.leaveRoom(() -> runOnUiThread(this::finish));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameManager.stopListening();
    }

    @Override
    public void onBackPressed() {
        showLeaveConfirmation();
    }
}
