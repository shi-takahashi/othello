package net.st_wet;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class TitleActivity extends AppCompatActivity {

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
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();  // CPU対戦は終了時にアプリを閉じるため
        });

        btnFriendBattle.setOnClickListener(v -> {
            Intent intent = new Intent(this, OnlineLobbyActivity.class);
            startActivity(intent);
            // 友達対戦は戻るでタイトルに戻れるようにfinish()しない
        });
    }
}
