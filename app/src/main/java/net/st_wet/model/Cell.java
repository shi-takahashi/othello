package net.st_wet.model;

import android.graphics.RectF;

public class Cell implements Cloneable
{
    public enum E_STATUS {
        None,
        Black,
        White
    }

    public static final String[] STATUS_NAME = {
        "",
        "黒",
        "白"
    };

    private int width;
    private int height;
    private int top;
    private int left;
    private E_STATUS status = E_STATUS.None;
    private float angle;

    public static E_STATUS getOppositeStatus(E_STATUS status) {
        if (status == E_STATUS.Black) {
            return E_STATUS.White;
        } else if (status == E_STATUS.White) {
            return E_STATUS.Black;
        }
        return E_STATUS.None;
    }

    @Override
    public Cell clone() {
        Cell clonedCell = null;
        try {
            clonedCell = (Cell)super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return clonedCell;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setTop(int top) {
        this.top = top;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public float getCx(){
        return this.left + this.width / 2f;
    }

    public float getCy(){
        return this.top + this.height / 2f;
    }

    public void setStatus(E_STATUS status) {
        this.setStatus(status, 0);
    }

    public void setStatus(E_STATUS status, float angle) {
        this.status = status;
        this.angle  = angle;
    }

    public E_STATUS getStatus() {
        return this.status;
    }

    public RectF getStoneRectF() {
        float stone_width  = this.width * 0.92f;
        float stone_height = this.height * 0.92f;
        float width_rate   =  1 - (this.angle / 90);

        float left   = getCx() - (stone_width / 2) * width_rate;
        float top    = getCy() - (stone_height / 2);
        float right  = getCx() + (stone_width / 2) * width_rate;
        float bottom = getCy() + (stone_height / 2);

        return new RectF(left, top, right, bottom);
    }

    public String statusToString() {
        return String.valueOf(this.status.ordinal());
    }
}
