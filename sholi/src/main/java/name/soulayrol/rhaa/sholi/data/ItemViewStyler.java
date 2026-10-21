package name.soulayrol.rhaa.sholi.data;

import android.graphics.Color;
import android.graphics.Paint;
import android.widget.TextView;

import name.soulayrol.rhaa.sholi.data.model.Checkable;

public final class ItemViewStyler {

    private ItemViewStyler() {
    }

    public static void bindName(TextView name, String text, int status, int textSize) {
        name.setText(text);
        name.setTextSize(textSize);
        applyStatus(name, status);
    }

    public static void bindName(TextView name, String text, Integer status) {
        name.setText(text);
        applyStatus(name, status);
    }

    public static void applyStatus(TextView name, Integer status) {
        applyStatus(name, status == null ? Checkable.OFF_LIST : status.intValue());
    }

    public static void applyStatus(TextView name, int status) {
        switch (status) {
            case Checkable.OFF_LIST:
                clearStrikeThrough(name);
                name.setTextColor(Color.GRAY);
                break;
            case Checkable.UNCHECKED:
                clearStrikeThrough(name);
                name.setTextColor(Color.GREEN);
                break;
            case Checkable.CHECKED:
                name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                name.setTextColor(Color.WHITE);
                break;
            default:
                clearStrikeThrough(name);
                name.setTextColor(Color.GRAY);
                break;
        }
    }

    private static void clearStrikeThrough(TextView name) {
        name.setPaintFlags(name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }
}
