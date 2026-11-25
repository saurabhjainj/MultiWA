package com.multiwa;

import android.app.Application;
import android.webkit.WebView;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Give every account process (":wa1".. ":wa5") its own WebView storage,
        // so cookies/logins never mix between accounts.
        String p = Application.getProcessName();
        int i = p.indexOf(":wa");
        if (i >= 0) WebView.setDataDirectorySuffix(p.substring(i + 1));
    }
}
