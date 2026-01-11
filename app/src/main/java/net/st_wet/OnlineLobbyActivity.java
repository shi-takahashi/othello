package net.st_wet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

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

    // ハンデ/ランダムモード設定用
    private MaterialButtonToggleGroup handicapTargetToggleGroup;
    private MaterialButtonToggleGroup handicapCountToggleGroup;
    private LinearLayout handicapCountContainer;
    private SwitchMaterial switchRandomMode;

    private boolean isWaiting = false;
    private int handicapTarget = 0;  // 0=なし, 1=黒(自分)にハンデ, 2=白(相手)にハンデ
    private int handicapCount = 1;
    private boolean isRandomMode = false;

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

        // ハンデ/ランダムモード設定用
        handicapTargetToggleGroup = findViewById(R.id.handicapTargetToggleGroup);
        handicapCountToggleGroup = findViewById(R.id.handicapCountToggleGroup);
        handicapCountContainer = findViewById(R.id.handicapCountContainer);
        switchRandomMode = findViewById(R.id.switchRandomMode);
    }

    private void setupListeners() {
        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        btnCancelWaiting.setOnClickListener(v -> cancelWaiting());
        btnBack.setOnClickListener(v -> finish());

        // ハンデ対象選択
        handicapTargetToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.handicapNone) {
                    handicapTarget = 0;
                    handicapCountContainer.setVisibility(View.GONE);
                } else if (checkedId == R.id.handicapPlayer) {
                    handicapTarget = 1;  // 自分（黒）にハンデ
                    handicapCountContainer.setVisibility(View.VISIBLE);
                    // ランダムモードをOFF
                    if (isRandomMode) {
                        isRandomMode = false;
                        switchRandomMode.setChecked(false);
                    }
                } else if (checkedId == R.id.handicapOpponent) {
                    handicapTarget = 2;  // 相手（白）にハンデ
                    handicapCountContainer.setVisibility(View.VISIBLE);
                    // ランダムモードをOFF
                    if (isRandomMode) {
                        isRandomMode = false;
                        switchRandomMode.setChecked(false);
                    }
                }
            }
        });

        // ハンデ個数選択
        handicapCountToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.handicapCount1) handicapCount = 1;
                else if (checkedId == R.id.handicapCount2) handicapCount = 2;
                else if (checkedId == R.id.handicapCount3) handicapCount = 3;
                else if (checkedId == R.id.handicapCount4) handicapCount = 4;
            }
        });

        // ランダムモード
        switchRandomMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isRandomMode = isChecked;
            if (isChecked) {
                // ハンデをOFF
                handicapTarget = 0;
                handicapTargetToggleGroup.check(R.id.handicapNone);
                handicapCountContainer.setVisibility(View.GONE);
            }
        });
    }

    private void createRoom() {
        btnCreateRoom.setEnabled(false);

        // 初期盤面を生成
        String initialBoard;
        if (isRandomMode) {
            // ランダムモード: OthelloViewでランダム盤面を生成
            OthelloView tempView = new OthelloView(this, null);
            initialBoard = tempView.generateRandomBoardString();
        } else if (handicapTarget != 0) {
            // ハンデモード: ハンデ付き初期盤面を生成
            initialBoard = FirestoreGameManager.getInitialBoardWithHandicap(handicapTarget, handicapCount);
        } else {
            // 通常モード
            initialBoard = FirestoreGameManager.getInitialBoardWithHandicap(0, 0);
        }

        gameManager.createRoom(initialBoard, handicapTarget, handicapCount, isRandomMode,
                new FirestoreGameManager.OnRoomCreatedListener() {
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
        intent.putExtra("handicapTarget", handicapTarget);
        intent.putExtra("handicapCount", handicapCount);
        intent.putExtra("isRandomMode", isRandomMode);
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
