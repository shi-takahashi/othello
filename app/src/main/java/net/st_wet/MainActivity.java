package net.st_wet;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.firebase.analytics.FirebaseAnalytics;

import net.st_wet.model.Cell;

import java.util.ArrayList;
import java.util.List;

import android.widget.TextView;

public class MainActivity extends AppCompatActivity
{
    private static final int REQUEST_CODE = 1;

    private AdView mAdView;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // SettingActivityから戻ってきた場合
            case (REQUEST_CODE):
                if (resultCode == RESULT_OK) {
                    boolean isNeedRestart = false;

                    // OKボタンを押して戻ってきたときの処理
                    if (data == null) {
                        return;
                    }

                    OthelloView othelloView = findViewById(R.id.othelloView);

                    int level = data.getIntExtra("level", 0);
                    if (level < 1 || level > 3) {
                        return;
                    }
                    int depth = level * 2 - 1;
                    if (othelloView.getDepth() != depth) {
                        othelloView.setDepth(depth);
                        // 要リスタート
                        isNeedRestart = true;
                    }

                    boolean isFirst = data.getBooleanExtra("first", true);
                    if (othelloView.getFirst() != isFirst) {
                        if (isFirst) {
                            othelloView.setTurn(Cell.E_STATUS.Black);
                        } else {
                            othelloView.setTurn(Cell.E_STATUS.White);
                        }
                        // 要リスタート
                        isNeedRestart = true;
                    }

                    int handicapTarget = data.getIntExtra("handicapTarget", 0);
                    int handicapCount = data.getIntExtra("handicapCount", 1);
                    if (othelloView.getHandicapTarget() != handicapTarget ||
                        othelloView.getHandicapCount() != handicapCount) {
                        othelloView.setHandicap(handicapTarget, handicapCount);
                        // 要リスタート
                        isNeedRestart = true;
                    }

                    boolean isReturnalbe = data.getBooleanExtra("returnable", false);
                    View btnBack = findViewById(R.id.btnBack);
                    btnBack.setVisibility(isReturnalbe ? View.VISIBLE : View.GONE);

                    boolean isShowLastMove = data.getBooleanExtra("showLastMove", true);
                    othelloView.setShowLastMove(isShowLastMove);

                    boolean isSoundEnabled = data.getBooleanExtra("soundEnabled", true);
                    othelloView.setSoundEnabled(isSoundEnabled);

                    boolean isReset = data.getBooleanExtra("reset", false);

                    // この後onResume()が走って復元されてしまうのでここで保存しておく
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putInt("level", level);
                    editor.putBoolean("first", isFirst);
                    editor.putInt("handicapTarget", handicapTarget);
                    editor.putInt("handicapCount", handicapCount);
                    editor.putBoolean("returnable", isReturnalbe);
                    editor.putBoolean("showLastMove", isShowLastMove);
                    editor.putBoolean("soundEnabled", isSoundEnabled);
                    if (isReset == true) {
                        editor.remove("resultOne");
                        editor.remove("resultTwo");
                        editor.remove("resultThree");
                    }
                    if (isNeedRestart == true) {
                        editor.remove("currentTurn");
                        editor.remove("cellsStatus");
                        editor.remove("history");
                        editor.remove("useBack");
                        this.restart();
                    }
                    editor.commit();

                } else if (resultCode == RESULT_CANCELED) {
                    // キャンセルボタンを押して戻ってきたときの処理
                } else {
                    // その他
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase Analytics 初期化
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                back();
            }
        });
        findViewById(R.id.btnEnd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                end();
            }
        });
        findViewById(R.id.btnLevelChange).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setting();
            }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu popup = new PopupMenu(getApplicationContext(), view);
                popup.getMenuInflater().inflate(R.menu.main, popup.getMenu());

                // アイコンを表示する
                try {
                    Field mPopup = popup.getClass().getDeclaredField("mPopup");
                    mPopup.setAccessible(true);
                    Object menuPopupHelper = mPopup.get(popup);
                    Method setForceShowIcon = menuPopupHelper.getClass()
                            .getDeclaredMethod("setForceShowIcon", boolean.class);
                    setForceShowIcon.setAccessible(true);
                    setForceShowIcon.invoke(menuPopupHelper, true);
                } catch (Exception e) {
                    // リフレクション失敗時は無視
                }

                // リリースビルドではDEBUGメニューを非表示
//                boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
//                if (!isDebuggable) {
//                    popup.getMenu().removeItem(R.id.menu_result_test);
//                }

                popup.show();

                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        int itemId = menuItem.getItemId();
                        if (itemId == R.id.menu_interrupt) {
                            interrupt();
                        } else if (itemId == R.id.menu_help) {
                            showHelp();
                        } else if (itemId == R.id.menu_result) {
                            result();
                        } else if (itemId == R.id.menu_setting) {
                            setting();
                        } else if (itemId == R.id.menu_end) {
                            end();
//                        } else if (itemId == R.id.menu_result_test) {
//                            resultTest();
                        }
                        return false;
                    }
                });
            }
        });
    }

    public void showHelp() {
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
    }

    public void restart() {
        OthelloView othelloView = findViewById(R.id.othelloView);
        othelloView.restart();
    }

    public void result() {
        Intent intent = new Intent(MainActivity.this, ResultActivity.class);
        intent.putExtra("level1", loadResult(1));
        intent.putExtra("level2", loadResult(2));
        intent.putExtra("level3", loadResult(3));
        startActivity(intent);
    }

    /**
     * [DEBUG] テストデータで成績画面を表示
     * 試行回数が多い場合のグラフ表示を確認するため
     */
    public void resultTest() {
        Intent intent = new Intent(MainActivity.this, ResultActivity.class);
        intent.putExtra("level1", generateTestData(150));  // 150回分
        intent.putExtra("level2", generateTestData(80));   // 80回分
        intent.putExtra("level3", generateTestData(200));  // 200回分
        startActivity(intent);
    }

    /**
     * テスト用のダミーデータを生成
     * @param count データ数
     * @return ダミーの成績データ（勝ち/負けがランダム）
     */
    private int[] generateTestData(int count) {
        java.util.Random random = new java.util.Random();
        int[] data = new int[count];
        for (int i = 0; i < count; i++) {
            // -64 〜 +64 の範囲でランダムな点数（0は引き分け）
            int point = random.nextInt(129) - 64;
            // 勝ちが少し多めになるよう調整
            if (point < -30) point += 20;
            data[i] = point;
        }
        return data;
    }

    public void setting() {
        Intent intent = new Intent(MainActivity.this, SettingActivity.class);

        OthelloView othelloView = findViewById(R.id.othelloView);

        int level = (othelloView.getDepth() + 1) / 2;
        intent.putExtra("level", level);

        boolean isFirst = othelloView.getFirst();
        intent.putExtra("first", isFirst);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean isReturnable = pref.getBoolean("returnable", false);
        intent.putExtra("returnable", isReturnable);

        int handicapTarget = pref.getInt("handicapTarget", 0);
        intent.putExtra("handicapTarget", handicapTarget);

        int handicapCount = pref.getInt("handicapCount", 1);
        intent.putExtra("handicapCount", handicapCount);

        boolean isShowLastMove = pref.getBoolean("showLastMove", true);
        intent.putExtra("showLastMove", isShowLastMove);

        boolean isSoundEnabled = pref.getBoolean("soundEnabled", true);
        intent.putExtra("soundEnabled", isSoundEnabled);

        startActivityForResult(intent, REQUEST_CODE);
    }

    public void back() {
        OthelloView othelloView = findViewById(R.id.othelloView);
        othelloView.back();
    }

    public void interrupt() {
        finishAndRemoveTask();
    }

    public void end() {
        OthelloView othelloView = findViewById(R.id.othelloView);
        othelloView.gameSet();
        finishAndRemoveTask();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = pref.edit();

        OthelloView othelloView = findViewById(R.id.othelloView);

        if (othelloView.getCurrentTurn() == Cell.E_STATUS.None) {
            editor.remove("currentTurn");
            editor.remove("cellsStatus");
            editor.remove("history");
            editor.remove("useBack");
        } else {
            editor.putInt("currentTurn", othelloView.getCurrentTurn().ordinal());
            editor.putString("cellsStatus", othelloView.getCellsStatus());
            editor.putString("history", othelloView.getHistory());
            editor.putBoolean("useBack", othelloView.getUseBack());
        }

        int level = (othelloView.getDepth() + 1) / 2;
        editor.putInt("level", level);

        boolean isFirst = othelloView.getFirst();
        editor.putBoolean("first", isFirst);

        boolean isUseBack = othelloView.getUseBack();
        editor.putBoolean("useBack", isUseBack);

        boolean isReturnalbe = findViewById(R.id.btnBack).getVisibility() == View.VISIBLE;
        editor.putBoolean("returnable", isReturnalbe);

        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int currentTurn = pref.getInt("currentTurn", 0);
        String cellsStatus = pref.getString("cellsStatus", null);
        String history = pref.getString("history", null);
        boolean bUseBack = pref.getBoolean("useBack", false);
        int level = pref.getInt("level", 2);
        boolean isFirst = pref.getBoolean("first", true);
        int handicapTarget = pref.getInt("handicapTarget", 0);
        int handicapCount = pref.getInt("handicapCount", 1);
        boolean isReturnalbe = pref.getBoolean("returnable", false);
        boolean isShowLastMove = pref.getBoolean("showLastMove", true);
        boolean isSoundEnabled = pref.getBoolean("soundEnabled", true);

        OthelloView othelloView = findViewById(R.id.othelloView);

        othelloView.setUseBack(bUseBack);

        int depth = level * 2 - 1;
        othelloView.setDepth(depth);

        if (isFirst) {
            othelloView.setTurn(Cell.E_STATUS.Black);
        } else {
            othelloView.setTurn(Cell.E_STATUS.White);
        }

        View btnBack = findViewById(R.id.btnBack);
        btnBack.setVisibility(isReturnalbe ? View.VISIBLE : View.GONE);

        othelloView.setShowLastMove(isShowLastMove);
        othelloView.setSoundEnabled(isSoundEnabled);
        othelloView.setHandicap(handicapTarget, handicapCount);

        if (history != null) {
            // 保存されたゲームがある場合は復元
            othelloView.restore(currentTurn, cellsStatus, history);
        } else {
            // 新規ゲームの場合はハンデ設定を適用して初期化
            othelloView.restart();
        }

        // レベル表示を更新（ハンデ設定後に呼ぶ）
        updateLevelDisplay(level);
    }

    public boolean getFirst() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean isFirst = pref.getBoolean("first", true);
        return isFirst;
    }

    public int[] loadResult(int level) {
        String key = "";

        switch (level) {
            case 1:
                key = "resultOne";
                break;
            case 2:
                key = "resultTwo";
                break;
            case 3:
                key = "resultThree";
                break;
            default:
                break;
        }

        if (key == "") {
            return null;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String result = pref.getString(key, null);

        if (result == null) {
            return null;
        }

        String[] sa = result.split(",", 0);
        int[] ia = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            ia[i] = Integer.parseInt(sa[i]);
        }

        return ia;
    }

    public void saveResult(int level, int point, boolean usedBack) {
        // 戻るボタンを使っていない場合のみローカルに保存
        if (!usedBack) {
            String key = "";

            switch (level) {
                case 1:
                    key = "resultOne";
                    break;
                case 2:
                    key = "resultTwo";
                    break;
                case 3:
                    key = "resultThree";
                    break;
                default:
                    break;
            }

            if (key != "") {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                String result = pref.getString(key, null);

                if (result == null) {
                    result = String.valueOf(point);
                } else {
                    result = result + "," + String.valueOf(point);
                }

                SharedPreferences.Editor editor = pref.edit();
                editor.putString(key, result);
                editor.commit();
            }
        }

        // Firebase Analytics にイベント送信（戻る使用時も送信）
        sendGameResultEvent(level, point, usedBack);
    }

    /**
     * レベル表示を更新
     * @param level レベル (1, 2, 3)
     */
    private void updateLevelDisplay(int level) {
        TextView txtLevel = findViewById(R.id.txtLevel);
        OthelloView othelloView = findViewById(R.id.othelloView);

        String text = "Lv." + level;
        if (othelloView.isHandicapEnabled()) {
            text += " (ハンデ)";
        }
        txtLevel.setText(text);
    }

    /**
     * 対戦結果を Firebase Analytics に送信
     * @param level レベル (1, 2, 3)
     * @param point 石差 (正: 勝ち, 負: 負け, 0: 引き分け)
     * @param usedBack 戻るボタンを使用したか
     */
    private void sendGameResultEvent(int level, int point, boolean usedBack) {
        String eventName = "game_result_lv" + level;
        if (usedBack) {
            eventName += "_matta";
        }

        String resultValue;
        if (point > 0) {
            resultValue = "win";
        } else if (point < 0) {
            resultValue = "lose";
        } else {
            resultValue = "draw";
        }

        Bundle params = new Bundle();
        params.putString("result", resultValue);
        params.putString("score_diff", String.valueOf(point));

        mFirebaseAnalytics.logEvent(eventName, params);
    }
}
