package com.moutamid.groupvoicecall;

import android.app.Application;

import com.moutamid.groupvoicecall.Model.CurrentUserSettings;
import com.moutamid.groupvoicecall.Model.WorkerThread;


public class AGApplication extends Application {

    private WorkerThread mWorkerThread;


    public synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(getApplicationContext());
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    public synchronized WorkerThread getWorkerThread() {
        return mWorkerThread;
    }

    public synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }

    public static final CurrentUserSettings mAudioSettings = new CurrentUserSettings();
}
