package net.st_wet;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    private final static String URL = "file:///android_asset/help.html";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

//        setTitle("やっぱりオセロ　ヘルプ");

        WebView webView = (WebView)findViewById(R.id.helpWebView);
        webView.loadUrl(URL);

        Button helpBack = (Button)findViewById(R.id.helpBack);
        helpBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(0);
                finish();
            }
        });

    }
}
