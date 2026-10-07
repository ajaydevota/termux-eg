package com.termux.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * GUI code editor opened by the `eg <filename>` command from the Termux terminal.
 * Has a Save button and a Back button (Back saves and returns to Termux).
 */
public class EditorActivity extends Activity {

    private EditText mEditor;
    private String mPath;
    private String mName = "untitled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        String p = getIntent().getStringExtra("file");
        if (p != null && p.startsWith("/")) {
            mPath = p;
            mName = new File(p).getName();
        } else if (p != null && !p.isEmpty()) {
            mName = p;
        }

        mEditor = (EditText) findViewById(R.id.editorText);
        mEditor.setTypeface(Typeface.MONOSPACE);
        mEditor.setTextSize(14f);
        mEditor.setGravity(Gravity.TOP | Gravity.START);
        mEditor.setHorizontallyScrolling(true);
        mEditor.setBackgroundColor(0xFF1E1E1E);
        mEditor.setTextColor(0xFFE6E6E6);

        ((TextView) findViewById(R.id.fileName)).setText(mName);

        findViewById(R.id.backBtn).setOnClickListener(v -> {
            save();
            finish();
        });
        findViewById(R.id.saveButton).setOnClickListener(v -> {
            if (save())
                Toast.makeText(this, "सेव हो गया: " + mName, Toast.LENGTH_SHORT).show();
        });

        buildKeys((LinearLayout) findViewById(R.id.keyRow));

        mEditor.setText(readFile());
        mEditor.setSelection(mEditor.getText().length());
        mEditor.addTextChangedListener(new CodeHighlighter(mEditor));
    }

    private String readFile() {
        if (mPath == null) return "";
        File f = new File(mPath);
        if (!f.exists() || !f.isFile()) return "";
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean save() {
        if (mPath == null) return false;
        FileOutputStream out = null;
        try {
            File f = new File(mPath);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new FileOutputStream(f);
            out.write(mEditor.getText().toString().getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void buildKeys(LinearLayout row) {
        String[] keys = {"(", ")", "[", "]", "{", "}", ":", "=", "+", "-", "*", "/",
                         "%", "\"", "'", "#", "_", ".", ",", "<", ">", "Tab"};
        for (final String k : keys) {
            Button b = new Button(this);
            b.setText(k);
            b.setTextSize(12f);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(18, 4, 18, 4);
            b.setOnClickListener(v -> {
                int start = Math.max(mEditor.getSelectionStart(), 0);
                int end = Math.max(mEditor.getSelectionEnd(), start);
                String text = k.equals("Tab") ? "    " : k;
                mEditor.getText().replace(start, end, text);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6, 0, 6, 0);
            row.addView(b, lp);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
