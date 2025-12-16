package net.st_wet;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingActivity extends AppCompatActivity
{
    private int level = 0;
    private boolean isFirst = true;
    private boolean isReturnalbe = true;
    private boolean isReset = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Intent intent = getIntent();

        level = intent.getIntExtra("level", 0);
        isFirst = intent.getBooleanExtra("first", true);
        isReturnalbe = intent.getBooleanExtra("returnable", true);
        isReset = false;

        loadLevel();
        loadTeban();
        loadReturnSetting();
        ((CheckBox) findViewById(R.id.checkReset)).setChecked(false);

        ((RadioGroup)findViewById(R.id.level)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.level1) {
                    level = 1;
                } else if (i == R.id.level2) {
                    level = 2;
                } else if (i == R.id.level3) {
                    level = 3;
                }
            }
        });

        ((RadioGroup)findViewById(R.id.teban)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.black) {
                    isFirst = true;
                } else if (i == R.id.white) {
                    isFirst = false;
                }
            }
        });

        findViewById(R.id.checkReturnable).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isReturnalbe = ((CheckBox)view).isChecked();
            }
        });

        findViewById(R.id.checkReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isReset = ((CheckBox)view).isChecked();
            }
        });

        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putExtra("level", level);
                intent.putExtra("first", isFirst);
                intent.putExtra("returnable", isReturnalbe);
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
        RadioButton radioButton = null;
        switch (level) {
            case 1:
                radioButton = (RadioButton)findViewById(R.id.level1);
                break;
            case 2:
                radioButton = (RadioButton)findViewById(R.id.level2);
                break;
            case 3:
                radioButton = (RadioButton)findViewById(R.id.level3);
                break;
            default:
                break;
        }
        if (radioButton != null) {
            radioButton.setChecked(true);
        }
    }

    private void loadTeban() {
        RadioButton radioButton = null;
        if (isFirst == true) {
            radioButton = (RadioButton) findViewById(R.id.black);
        } else {
            radioButton = (RadioButton) findViewById(R.id.white);
        }
        if (radioButton != null) {
            radioButton.setChecked(true);
        }
    }

    private void loadReturnSetting() {
        CheckBox checkBox = (CheckBox) findViewById(R.id.checkReturnable);
        checkBox.setChecked(isReturnalbe);
    }
}
