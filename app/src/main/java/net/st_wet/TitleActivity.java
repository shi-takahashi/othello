package net.st_wet;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class TitleActivity extends AppCompatActivity {

    private static final String PREF_SKIP_QUICK_SETTINGS = "skipQuickSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // アクションバーを非表示
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_title);

        MaterialButton btnCpuBattle = findViewById(R.id.btnCpuBattle);
        MaterialButton btnFriendBattle = findViewById(R.id.btnFriendBattle);

        btnCpuBattle.setOnClickListener(v -> {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            boolean skipDialog = pref.getBoolean(PREF_SKIP_QUICK_SETTINGS, false);

            // 中断中の対局がある場合はダイアログをスキップ
            String cellsStatus = pref.getString("cellsStatus", null);
            boolean hasSuspendedGame = (cellsStatus != null);

            if (skipDialog || hasSuspendedGame) {
                startCpuBattle();
            } else {
                showQuickSettingsDialog();
            }
        });

        btnFriendBattle.setOnClickListener(v -> {
            Intent intent = new Intent(this, OnlineLobbyActivity.class);
            startActivity(intent);
            // 友達対戦は戻るでタイトルに戻れるようにfinish()しない
        });
    }

    private void showQuickSettingsDialog() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

        // 現在の設定を読み込み
        int currentLevel = pref.getInt("level", 2);
        boolean currentFirst = pref.getBoolean("first", true);
        int currentHandicapTarget = pref.getInt("handicapTarget", 0);
        int currentHandicapCount = pref.getInt("handicapCount", 1);
        boolean currentRandomMode = pref.getBoolean("randomMode", false);
        boolean currentReturnable = pref.getBoolean("returnable", false);

        // ダイアログのビューを作成
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quick_settings, null);

        // UI要素を取得
        MaterialButtonToggleGroup levelToggleGroup = dialogView.findViewById(R.id.levelToggleGroup);
        MaterialButtonToggleGroup tebanToggleGroup = dialogView.findViewById(R.id.tebanToggleGroup);
        MaterialButtonToggleGroup handicapTargetToggleGroup = dialogView.findViewById(R.id.handicapTargetToggleGroup);
        MaterialButtonToggleGroup handicapCountToggleGroup = dialogView.findViewById(R.id.handicapCountToggleGroup);
        View handicapCountContainer = dialogView.findViewById(R.id.handicapCountContainer);
        SwitchMaterial switchRandomMode = dialogView.findViewById(R.id.switchRandomMode);
        SwitchMaterial switchReturnable = dialogView.findViewById(R.id.switchReturnable);
        CheckBox checkDontShowAgain = dialogView.findViewById(R.id.checkDontShowAgain);

        // 現在の設定を反映
        // レベル
        switch (currentLevel) {
            case 1: levelToggleGroup.check(R.id.level1); break;
            case 3: levelToggleGroup.check(R.id.level3); break;
            default: levelToggleGroup.check(R.id.level2); break;
        }

        // 手番
        if (currentFirst) {
            tebanToggleGroup.check(R.id.black);
        } else {
            tebanToggleGroup.check(R.id.white);
        }

        // ハンディキャップ
        switch (currentHandicapTarget) {
            case 1:
                handicapTargetToggleGroup.check(R.id.handicapPlayer);
                handicapCountContainer.setVisibility(View.VISIBLE);
                break;
            case 2:
                handicapTargetToggleGroup.check(R.id.handicapCpu);
                handicapCountContainer.setVisibility(View.VISIBLE);
                break;
            default:
                handicapTargetToggleGroup.check(R.id.handicapNone);
                handicapCountContainer.setVisibility(View.GONE);
                break;
        }

        // ハンディキャップの数
        switch (currentHandicapCount) {
            case 2: handicapCountToggleGroup.check(R.id.handicapCount2); break;
            case 3: handicapCountToggleGroup.check(R.id.handicapCount3); break;
            case 4: handicapCountToggleGroup.check(R.id.handicapCount4); break;
            default: handicapCountToggleGroup.check(R.id.handicapCount1); break;
        }

        // ランダムモード
        switchRandomMode.setChecked(currentRandomMode);

        // 待った
        switchReturnable.setChecked(currentReturnable);

        // ハンデとランダムの相互排他リスナー
        handicapTargetToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.handicapNone) {
                    handicapCountContainer.setVisibility(View.GONE);
                } else {
                    handicapCountContainer.setVisibility(View.VISIBLE);
                    // ハンデを選択したらランダムモードをOFF
                    switchRandomMode.setChecked(false);
                }
            }
        });

        switchRandomMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // ランダムモードをONにしたらハンデをなしに
                handicapTargetToggleGroup.check(R.id.handicapNone);
                handicapCountContainer.setVisibility(View.GONE);
            }
        });

        // ダイアログを表示
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("対局設定")
                .setView(dialogView)
                .setPositiveButton("開始", (d, which) -> {
                    // 設定を取得
                    int level = 2;
                    int levelCheckedId = levelToggleGroup.getCheckedButtonId();
                    if (levelCheckedId == R.id.level1) level = 1;
                    else if (levelCheckedId == R.id.level3) level = 3;

                    boolean isFirst = tebanToggleGroup.getCheckedButtonId() == R.id.black;

                    int handicapTarget = 0;
                    int handicapTargetCheckedId = handicapTargetToggleGroup.getCheckedButtonId();
                    if (handicapTargetCheckedId == R.id.handicapPlayer) handicapTarget = 1;
                    else if (handicapTargetCheckedId == R.id.handicapCpu) handicapTarget = 2;

                    int handicapCount = 1;
                    int handicapCountCheckedId = handicapCountToggleGroup.getCheckedButtonId();
                    if (handicapCountCheckedId == R.id.handicapCount2) handicapCount = 2;
                    else if (handicapCountCheckedId == R.id.handicapCount3) handicapCount = 3;
                    else if (handicapCountCheckedId == R.id.handicapCount4) handicapCount = 4;

                    boolean isRandomMode = switchRandomMode.isChecked();
                    boolean isReturnable = switchReturnable.isChecked();
                    boolean dontShowAgain = checkDontShowAgain.isChecked();

                    // SharedPreferencesに保存
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putInt("level", level);
                    editor.putBoolean("first", isFirst);
                    editor.putInt("handicapTarget", handicapTarget);
                    editor.putInt("handicapCount", handicapCount);
                    editor.putBoolean("randomMode", isRandomMode);
                    editor.putBoolean("returnable", isReturnable);
                    editor.putBoolean(PREF_SKIP_QUICK_SETTINGS, dontShowAgain);
                    // 設定が変わったので対局状態をクリア
                    editor.remove("currentTurn");
                    editor.remove("cellsStatus");
                    editor.remove("history");
                    editor.remove("useBack");
                    editor.apply();

                    startCpuBattle();
                })
                .setNegativeButton("キャンセル", null)
                .create();

        dialog.show();
    }

    private void startCpuBattle() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
