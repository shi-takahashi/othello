package net.st_wet;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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

        createChart(R.id.line_chart1, chart_data1);
        createChart(R.id.line_chart2, chart_data2);
        createChart(R.id.line_chart3, chart_data3);

        Button btnReturn = (Button)findViewById(R.id.btnReturn);
        btnReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void createChart(int id, int[] _data) {
        int win = 0;
        int lose = 0;
        int sum = 0;
        int min = -10;
        int max = 10;
        ArrayList<Entry> values = new ArrayList<>();
        if (_data == null) {
            values.add(new Entry(0, 0, null, null));
        } else {
            int data[] = new int[_data.length];
            for (int i = 0; i < _data.length; i++) {
                if (_data[i] > 0) {
                    win++;
                }
                if (_data[i] < 0) {
                    lose++;
                }
                sum += _data[i];
                data[i] = sum;
                if (sum < min) {
                    min = sum;
                }
                if (sum > max) {
                    max = sum;
                }
            }
            for (int i = 0; i < data.length; i++) {
                values.add(new Entry(i+1, data[i], null, null));
            }
        }

        LineChart mChart = findViewById(id);

        // Grid背景色
        mChart.setDrawGridBackground(true);

        // no description text
        mChart.getDescription().setEnabled(true);
        String desc = String.valueOf(win) + "勝" + String.valueOf(lose) + "負";
        String point = String.valueOf(sum + "点");
        mChart.getDescription().setText(point);
        mChart.getDescription().setTextSize(12f);
        mChart.getDescription().setYOffset(-10f);

        // X軸
        XAxis xAxis = mChart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setLabelCount(5);
        xAxis.enableGridDashedLine(10f, 10f, 0f);   // Grid横軸を破線
//        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);  // 下軸
        xAxis.setAxisMinimum(1f);
        xAxis.setAxisMaximum(values.size() > 10f ? values.size() : 10f);
        // 整数表示に
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
//                return super.getFormattedValue(value);
                return "" + (int) value;
            }
        });

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setEnabled(true);
        leftAxis.setLabelCount(5);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);    // Grid横軸を破線
//        leftAxis.setDrawZeroLine(true);
        // Y軸最大最小設定
        leftAxis.setAxisMinimum(min < -10f ? min : -10f);
        leftAxis.setAxisMaximum(max > 10f ? max : 10f);
        // 整数表示に
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "" + (int) value;
            }
        });

        // 右側の目盛り(非表示)
        mChart.getAxisRight().setEnabled(false);

        int color = 0;
        String label = "";

        if (id == R.id.line_chart1) {
            color = Color.BLACK;
            label = "Lv1";
        } else if (id == R.id.line_chart2) {
            color = Color.BLUE;
            label = "Lv2";
        } else if (id == R.id.line_chart3) {
            color = Color.RED;
            label = "Lv3";
        }

        LineDataSet set1 = new LineDataSet(values, label + " (" + desc + ")");

        set1.setDrawIcons(false);           // ?
        set1.setColor(color);               // 線の色
        set1.setLineWidth(1f);              // 線の太さ
        set1.setCircleColor(color);         // 点の色
        set1.setDrawCircles(true);
        int size = values.size();
        if (size > 100) {                   //　点の太さ
            set1.setDrawCircles(false);
        } else if (size > 30) {
            set1.setCircleRadius(1f);
        } else if (size > 10) {
            set1.setCircleRadius(2f);
        } else {
            set1.setCircleRadius(3f);
        }
        set1.setDrawCircleHole(false);      // 点の真ん中を表示するか
        set1.setValueTextSize(0f);          // 点の数値の表示サイズ（0だと表示しない）
        // 整数表示に
        set1.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "" + (int) value;
            }
        });
        set1.setDrawFilled(false);          // 囲み領域を塗りつぶすか
//        set1.setFillColor(Color.BLUE);      // 塗りつぶす場合の色
        set1.setFormLineWidth(1f);          // ?
        set1.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));   // ?
        set1.setFormSize(14.f);             // 凡例画像大きさ

        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
        dataSets.add(set1); // add the datasets

        // create a data object with the datasets
        LineData lineData = new LineData(dataSets);

        // set data
        mChart.setData(lineData);

        mChart.invalidate();
    }
}
