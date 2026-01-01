package net.st_wet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import net.st_wet.util.FirestoreGameManager;

public class OnlineLobbyActivity extends AppCompatActivity {

    private FirestoreGameManager gameManager;

    private MaterialButton btnCreateRoom;
    private MaterialButton btnJoinRoom;
    private MaterialButton btnCancelWaiting;
    private MaterialButton btnBack;
    private EditText etRoomCode;
    private MaterialCardView cardWaiting;
    private TextView txtRoomCode;

    private boolean isWaiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_lobby);

        gameManager = new FirestoreGameManager(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        btnCancelWaiting = findViewById(R.id.btnCancelWaiting);
        btnBack = findViewById(R.id.btnBack);
        etRoomCode = findViewById(R.id.etRoomCode);
        cardWaiting = findViewById(R.id.cardWaiting);
        txtRoomCode = findViewById(R.id.txtRoomCode);
    }

    private void setupListeners() {
        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        btnCancelWaiting.setOnClickListener(v -> cancelWaiting());
        btnBack.setOnClickListener(v -> finish());
    }

    private void createRoom() {
        btnCreateRoom.setEnabled(false);

        gameManager.createRoom(new FirestoreGameManager.OnRoomCreatedListener() {
            @Override
            public void onSuccess(String roomCode) {
                runOnUiThread(() -> {
                    showWaitingState(roomCode);
                    startListeningForOpponent(roomCode);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    btnCreateRoom.setEnabled(true);
                    Toast.makeText(OnlineLobbyActivity.this,
                            "ルーム作成に失敗しました: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void joinRoom() {
        String roomCode = etRoomCode.getText().toString().trim().toUpperCase();

        if (roomCode.length() != 6) {
            Toast.makeText(this, "6桁のルームコードを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }

        btnJoinRoom.setEnabled(false);

        gameManager.joinRoom(roomCode, new FirestoreGameManager.OnRoomJoinedListener() {
            @Override
            public void onSuccess(String myColor) {
                runOnUiThread(() -> {
                    startGame(roomCode, myColor);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    btnJoinRoom.setEnabled(true);
                    Toast.makeText(OnlineLobbyActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showWaitingState(String roomCode) {
        isWaiting = true;
        cardWaiting.setVisibility(View.VISIBLE);
        txtRoomCode.setText(roomCode);
        btnCreateRoom.setVisibility(View.GONE);
        findViewById(R.id.btnJoinRoom).setEnabled(false);
        etRoomCode.setEnabled(false);
    }

    private void hideWaitingState() {
        isWaiting = false;
        cardWaiting.setVisibility(View.GONE);
        btnCreateRoom.setVisibility(View.VISIBLE);
        btnCreateRoom.setEnabled(true);
        btnJoinRoom.setEnabled(true);
        etRoomCode.setEnabled(true);
    }

    private void startListeningForOpponent(String roomCode) {
        gameManager.startListening(roomCode, new FirestoreGameManager.OnGameStateChangedListener() {
            @Override
            public void onGameStateChanged(FirestoreGameManager.GameState state) {
                // 相手が参加したらゲーム開始
                if ("playing".equals(state.status) && state.playerWhite != null) {
                    runOnUiThread(() -> {
                        startGame(roomCode, "black");
                    });
                }
            }

            @Override
            public void onOpponentJoined() {
                // onGameStateChangedで処理される
            }

            @Override
            public void onOpponentLeft() {
                runOnUiThread(() -> {
                    if (isWaiting) {
                        Toast.makeText(OnlineLobbyActivity.this,
                                "ルームが削除されました", Toast.LENGTH_SHORT).show();
                        hideWaitingState();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(OnlineLobbyActivity.this,
                            "エラー: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void cancelWaiting() {
        gameManager.stopListening();
        gameManager.leaveRoom(() -> {
            runOnUiThread(this::hideWaitingState);
        });
    }

    private void startGame(String roomCode, String myColor) {
        // ゲーム開始時はルームを削除しない
        isWaiting = false;
        gameManager.stopListening();

        Intent intent = new Intent(this, OnlineGameActivity.class);
        intent.putExtra("roomCode", roomCode);
        intent.putExtra("myColor", myColor);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isWaiting) {
            gameManager.stopListening();
            gameManager.leaveRoom(null);
        }
    }

    @Override
    public void onBackPressed() {
        if (isWaiting) {
            cancelWaiting();
        } else {
            super.onBackPressed();
        }
    }
}
