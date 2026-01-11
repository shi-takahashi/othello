package net.st_wet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends AppCompatActivity
{
    private static final String PREF_SKIP_QUICK_SETTINGS = "skipQuickSettings";

    private int level = 0;
    private boolean isFirst = true;
    private int handicapTarget = 0;  // 0=なし, 1=自分, 2=相手
    private int handicapCount = 1;   // 1〜4
    private boolean isRandomMode = false;
    private boolean isReturnalbe = true;
    private boolean isShowLastMove = true;
    private boolean isSoundEnabled = true;
    private boolean isReset = false;
    private boolean isShowQuickSettings = true;

    private MaterialButtonToggleGroup levelToggleGroup;
    private MaterialButtonToggleGroup tebanToggleGroup;
    private MaterialButtonToggleGroup handicapTargetToggleGroup;
    private MaterialButtonToggleGroup handicapCountToggleGroup;
    private View handicapCountContainer;
    private View handicapCard;
    private SwitchMaterial switchRandomMode;
    private SwitchMaterial switchReturnable;
    private SwitchMaterial switchShowLastMove;
    private SwitchMaterial switchSound;
    private SwitchMaterial switchReset;
    private SwitchMaterial switchShowQuickSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Intent intent = getIntent();

        level = intent.getIntExtra("level", 0);
        isFirst = intent.getBooleanExtra("first", true);
        handicapTarget = intent.getIntExtra("handicapTarget", 0);
        handicapCount = intent.getIntExtra("handicapCount", 1);
        isRandomMode = intent.getBooleanExtra("randomMode", false);
        isReturnalbe = intent.getBooleanExtra("returnable", false);
        isShowLastMove = intent.getBooleanExtra("showLastMove", true);
        isSoundEnabled = intent.getBooleanExtra("soundEnabled", true);
        isReset = false;

        // Initialize views
        levelToggleGroup = findViewById(R.id.levelToggleGroup);
        tebanToggleGroup = findViewById(R.id.tebanToggleGroup);
        handicapTargetToggleGroup = findViewById(R.id.handicapTargetToggleGroup);
        handicapCountToggleGroup = findViewById(R.id.handicapCountToggleGroup);
        handicapCountContainer = findViewById(R.id.handicapCountContainer);
        handicapCard = findViewById(R.id.handicapCard);
        switchRandomMode = findViewById(R.id.switchRandomMode);
        switchReturnable = findViewById(R.id.switchReturnable);
        switchShowLastMove = findViewById(R.id.switchShowLastMove);
        switchSound = findViewById(R.id.switchSound);
        switchReset = findViewById(R.id.switchReset);
        switchShowQuickSettings = findViewById(R.id.switchShowQuickSettings);

        // SharedPreferencesから起動時ダイアログ設定を読み込み
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        isShowQuickSettings = !pref.getBoolean(PREF_SKIP_QUICK_SETTINGS, false);

        loadLevel();
        loadTeban();
        loadHandicap();
        loadRandomMode();
        loadReturnSetting();
        switchShowLastMove.setChecked(isShowLastMove);
        switchSound.setChecked(isSoundEnabled);
        switchReset.setChecked(false);

        // Level selection listener
        levelToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    if (checkedId == R.id.level1) {
                        level = 1;
                    } else if (checkedId == R.id.level2) {
                        level = 2;
                    } else if (checkedId == R.id.level3) {
                        level = 3;
                    }
                }
            }
        });

        // Turn selection listener
        tebanToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    if (checkedId == R.id.black) {
                        isFirst = true;
                    } else if (checkedId == R.id.white) {
                        isFirst = false;
                    }
                }
            }
        });

        // Handicap target selection listener
        handicapTargetToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    if (checkedId == R.id.handicapNone) {
                        handicapTarget = 0;
                        handicapCountContainer.setVisibility(View.GONE);
                    } else if (checkedId == R.id.handicapPlayer) {
                        handicapTarget = 1;
                        handicapCountContainer.setVisibility(View.VISIBLE);
                        // ハンデを選択したらランダムモードをOFFに
                        isRandomMode = false;
                        switchRandomMode.setChecked(false);
                    } else if (checkedId == R.id.handicapCpu) {
                        handicapTarget = 2;
                        handicapCountContainer.setVisibility(View.VISIBLE);
                        // ハンデを選択したらランダムモードをOFFに
                        isRandomMode = false;
                        switchRandomMode.setChecked(false);
                    }
                }
            }
        });

        // Handicap count selection listener
        handicapCountToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    if (checkedId == R.id.handicapCount1) {
                        handicapCount = 1;
                    } else if (checkedId == R.id.handicapCount2) {
                        handicapCount = 2;
                    } else if (checkedId == R.id.handicapCount3) {
                        handicapCount = 3;
                    } else if (checkedId == R.id.handicapCount4) {
                        handicapCount = 4;
                    }
                }
            }
        });

        // Switch listeners
        switchRandomMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isRandomMode = isChecked;
            if (isChecked) {
                // ランダムモードをONにしたらハンディキャップをなしに
                handicapTarget = 0;
                handicapTargetToggleGroup.check(R.id.handicapNone);
                handicapCountContainer.setVisibility(View.GONE);
            }
        });

        switchReturnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReturnalbe = isChecked;
        });

        switchShowLastMove.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isShowLastMove = isChecked;
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSoundEnabled = isChecked;
        });

        switchShowQuickSettings.setChecked(isShowQuickSettings);
        switchShowQuickSettings.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isShowQuickSettings = isChecked;
        });

        switchReset.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReset = isChecked;
        });

        // Button listeners
        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 起動時ダイアログ設定をSharedPreferencesに保存
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(SettingActivity.this).edit();
                editor.putBoolean(PREF_SKIP_QUICK_SETTINGS, !isShowQuickSettings);
                editor.apply();

                Intent intent = new Intent();
                intent.putExtra("level", level);
                intent.putExtra("first", isFirst);
                intent.putExtra("handicapTarget", handicapTarget);
                intent.putExtra("handicapCount", handicapCount);
                intent.putExtra("randomMode", isRandomMode);
                intent.putExtra("returnable", isReturnalbe);
                intent.putExtra("showLastMove", isShowLastMove);
                intent.putExtra("soundEnabled", isSoundEnabled);
                intent.putExtra("reset", isReset);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    private void loadLevel() {
        int buttonId = R.id.level1;
        switch (level) {
            case 1:
                buttonId = R.id.level1;
                break;
            case 2:
                buttonId = R.id.level2;
                break;
            case 3:
                buttonId = R.id.level3;
                break;
            default:
                buttonId = R.id.level1;
                break;
        }
        levelToggleGroup.check(buttonId);
    }

    private void loadTeban() {
        if (isFirst) {
            tebanToggleGroup.check(R.id.black);
        } else {
            tebanToggleGroup.check(R.id.white);
        }
    }

    private void loadReturnSetting() {
        switchReturnable.setChecked(isReturnalbe);
    }

    private void loadRandomMode() {
        switchRandomMode.setChecked(isRandomMode);
    }

    private void loadHandicap() {
        // Target selection
        int targetButtonId = R.id.handicapNone;
        switch (handicapTarget) {
            case 0:
                targetButtonId = R.id.handicapNone;
                handicapCountContainer.setVisibility(View.GONE);
                break;
            case 1:
                targetButtonId = R.id.handicapPlayer;
                handicapCountContainer.setVisibility(View.VISIBLE);
                break;
            case 2:
                targetButtonId = R.id.handicapCpu;
                handicapCountContainer.setVisibility(View.VISIBLE);
                break;
        }
        handicapTargetToggleGroup.check(targetButtonId);

        // Count selection
        int countButtonId = R.id.handicapCount1;
        switch (handicapCount) {
            case 1:
                countButtonId = R.id.handicapCount1;
                break;
            case 2:
                countButtonId = R.id.handicapCount2;
                break;
            case 3:
                countButtonId = R.id.handicapCount3;
                break;
            case 4:
                countButtonId = R.id.handicapCount4;
                break;
        }
        handicapCountToggleGroup.check(countButtonId);
    }
}
