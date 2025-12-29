package net.st_wet;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Intent intent = getIntent();
        int[] chart_data1 = intent.getIntArrayExtra("level1");
        int[] chart_data2 = intent.getIntArrayExtra("level2");
        int[] chart_data3 = intent.getIntArrayExtra("level3");

        // 成績テキストを更新
        updateScoreText(R.id.textLv1, "Lv.1", chart_data1);
        updateScoreText(R.id.textLv2, "Lv.2", chart_data2);
        updateScoreText(R.id.textLv3, "Lv.3", chart_data3);

        // 統合グラフを作成
        createCombinedChart(chart_data1, chart_data2, chart_data3);

        Button btnReturn = (Button)findViewById(R.id.btnReturn);
        btnReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void updateScoreText(int textViewId, String levelLabel, int[] data) {
        TextView textView = findViewById(textViewId);
        int win = 0;
        int lose = 0;
        int sum = 0;
        int count = 0;

        if (data != null) {
            count = data.length;
            for (int value : data) {
                if (value > 0) {
                    win++;
                    sum += value;  // 勝った時のみ加算
                }
                if (value < 0) {
                    lose++;
                    // 負けは0点扱い（加算しない）
                }
            }
        }

        // 平均点を計算（試行回数が0の場合は0）
        float average = count > 0 ? (float) sum / count : 0;
        String text = levelLabel + "  " + win + "勝" + lose + "敗　" + String.format("%.1f", average) + "点";
        textView.setText(text);
    }

    private void createCombinedChart(int[] data1, int[] data2, int[] data3) {
        LineChart mChart = findViewById(R.id.line_chart);

        // データがない場合のメッセージを設定
        mChart.setNoDataText("同じレベルで10回以上プレイすると\nグラフが表示されます");

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();

        float globalMin = 0;  // 最小値は0（マイナスにならない）
        float globalMax = 10;
        int maxDataLength = 10;

        // Lv1のデータセット作成
        ChartDataResult result1 = createDataSet(data1, Color.BLACK, "Lv1");
        if (result1 != null) {
            dataSets.add(result1.dataSet);
            globalMin = Math.min(globalMin, result1.min);
            globalMax = Math.max(globalMax, result1.max);
            maxDataLength = Math.max(maxDataLength, result1.dataLength);
        }

        // Lv2のデータセット作成
        ChartDataResult result2 = createDataSet(data2, Color.BLUE, "Lv2");
        if (result2 != null) {
            dataSets.add(result2.dataSet);
            globalMin = Math.min(globalMin, result2.min);
            globalMax = Math.max(globalMax, result2.max);
            maxDataLength = Math.max(maxDataLength, result2.dataLength);
        }

        // Lv3のデータセット作成
        ChartDataResult result3 = createDataSet(data3, Color.RED, "Lv3");
        if (result3 != null) {
            dataSets.add(result3.dataSet);
            globalMin = Math.min(globalMin, result3.min);
            globalMax = Math.max(globalMax, result3.max);
            maxDataLength = Math.max(maxDataLength, result3.dataLength);
        }

        // Grid背景色
        mChart.setDrawGridBackground(true);

        // no description text
        mChart.getDescription().setEnabled(false);

        // X軸
        XAxis xAxis = mChart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setLabelCount(5);
        xAxis.enableGridDashedLine(10f, 10f, 0f);
        xAxis.setAxisMinimum(10f);
        xAxis.setAxisMaximum(maxDataLength);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "" + (int) value;
            }
        });

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setEnabled(true);
        leftAxis.setLabelCount(5);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setAxisMinimum(globalMin);
        leftAxis.setAxisMaximum(globalMax);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "" + (int) value;
            }
        });

        // 右側の目盛り(非表示)
        mChart.getAxisRight().setEnabled(false);

        // データがある場合のみ表示
        if (!dataSets.isEmpty()) {
            LineData lineData = new LineData(dataSets);
            mChart.setData(lineData);
        }

        mChart.invalidate();
    }

    private static class ChartDataResult {
        LineDataSet dataSet;
        float min;
        float max;
        int dataLength;

        ChartDataResult(LineDataSet dataSet, float min, float max, int dataLength) {
            this.dataSet = dataSet;
            this.min = min;
            this.max = max;
            this.dataLength = dataLength;
        }
    }

    private ChartDataResult createDataSet(int[] _data, int color, String label) {
        // 試行回数10回未満はグラフに表示しない
        if (_data == null || _data.length < 10) {
            return null;
        }

        int sum = 0;
        float min = 0;  // 最小値は0（マイナスにならない）
        float max = 0;
        ArrayList<Entry> values = new ArrayList<>();

        for (int i = 0; i < _data.length; i++) {
            // マイナスは0扱い（勝った時のみ加算）
            int point = _data[i] > 0 ? _data[i] : 0;
            sum += point;
            // 10回目以降のみグラフに表示（1〜9回目は平均がブレやすいため除外）
            if (i >= 9) {
                // 各時点での平均を計算
                float average = (float) sum / (i + 1);
                values.add(new Entry(i + 1, average, null, null));
                if (average > max) {
                    max = average;
                }
            }
        }

        LineDataSet dataSet = new LineDataSet(values, label);

        dataSet.setDrawIcons(false);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(color);
        dataSet.setDrawCircles(true);

        int size = values.size();
        if (size > 100) {
            dataSet.setDrawCircles(false);
        } else if (size > 30) {
            dataSet.setCircleRadius(1f);
        } else if (size > 10) {
            dataSet.setCircleRadius(2f);
        } else {
            dataSet.setCircleRadius(3f);
        }

        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(0f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "" + (int) value;
            }
        });
        dataSet.setDrawFilled(false);
        dataSet.setFormLineWidth(1f);
        dataSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
        dataSet.setFormSize(14.f);

        return new ChartDataResult(dataSet, min, max, values.size());
    }
}
