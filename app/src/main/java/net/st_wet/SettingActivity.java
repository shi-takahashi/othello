package net.st_wet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends AppCompatActivity
{
    private int level = 0;
    private boolean isFirst = true;
    private boolean isReturnalbe = true;
    private boolean isShowLastMove = true;
    private boolean isReset = false;

    private MaterialButtonToggleGroup levelToggleGroup;
    private MaterialButtonToggleGroup tebanToggleGroup;
    private SwitchMaterial switchReturnable;
    private SwitchMaterial switchShowLastMove;
    private SwitchMaterial switchReset;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Intent intent = getIntent();

        level = intent.getIntExtra("level", 0);
        isFirst = intent.getBooleanExtra("first", true);
        isReturnalbe = intent.getBooleanExtra("returnable", false);
        isShowLastMove = intent.getBooleanExtra("showLastMove", true);
        isReset = false;

        // Initialize views
        levelToggleGroup = findViewById(R.id.levelToggleGroup);
        tebanToggleGroup = findViewById(R.id.tebanToggleGroup);
        switchReturnable = findViewById(R.id.switchReturnable);
        switchShowLastMove = findViewById(R.id.switchShowLastMove);
        switchReset = findViewById(R.id.switchReset);

        loadLevel();
        loadTeban();
        loadReturnSetting();
        switchShowLastMove.setChecked(isShowLastMove);
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

        // Switch listeners
        switchReturnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReturnalbe = isChecked;
        });

        switchShowLastMove.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isShowLastMove = isChecked;
        });

        switchReset.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReset = isChecked;
        });

        // Button listeners
        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putExtra("level", level);
                intent.putExtra("first", isFirst);
                intent.putExtra("returnable", isReturnalbe);
                intent.putExtra("showLastMove", isShowLastMove);
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
}
