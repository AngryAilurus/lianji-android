package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** User-started, bounded rest timer; independent of the training screen lifecycle. */
public final class RestTimerService extends Service {
    static final int ID=41;
    static final String CHANNEL="rest_timer", FINISHED="rest_finished", STOP="stop_rest";
    final Handler handler=new Handler();
    PowerManager.WakeLock wakeLock;
    long deadline;
    int session=-1;
    final Runnable finishTimer=()->finishRest();
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("MainActivity",MODE_PRIVATE);}
    @Override public void onCreate(){super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"组间休息倒计时",NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(FINISHED,"休息结束提醒",NotificationManager.IMPORTANCE_DEFAULT));
    }
    PendingIntent open(){Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("timerSession",session);return PendingIntent.getActivity(this,41,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    Notification.Builder base(String channel){return new Notification.Builder(this,channel).setSmallIcon(R.drawable.ic_rest_timer).setContentIntent(open()).setCategory(Notification.CATEGORY_STOPWATCH).setVisibility(Notification.VISIBILITY_PUBLIC);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){prefs(this).edit().remove("timerEnd").apply();stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(ID);stopSelf();return START_NOT_STICKY;}
        long end=intent!=null?intent.getLongExtra("end",prefs(this).getLong("timerEnd",0)):prefs(this).getLong("timerEnd",0);
        session=intent!=null?intent.getIntExtra("session",prefs(this).getInt("timerSession",-1)):prefs(this).getInt("timerSession",-1);
        long remaining=Math.min(1800000L,Math.max(0,end-System.currentTimeMillis()));
        deadline=SystemClock.elapsedRealtime()+remaining;
        prefs(this).edit().putLong("timerEnd",end).putInt("timerSession",session).apply();
        PendingIntent stop=PendingIntent.getService(this,42,new Intent(this,RestTimerService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=base(CHANNEL).setContentTitle("练记 · 组间休息").setContentText("休息后继续下一组").setWhen(System.currentTimeMillis()+remaining).setUsesChronometer(true).setChronometerCountDown(true).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"结束休息",stop).build()).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(ID,notification);
        handler.removeCallbacks(finishTimer);
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        if(remaining==0){finishRest();return START_NOT_STICKY;}
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"lianji:rest");wakeLock.acquire(remaining+5000);
        handler.postDelayed(finishTimer,remaining);
        return START_STICKY;
    }
    void finishRest(){prefs(this).edit().remove("timerEnd").apply();stopForeground(STOP_FOREGROUND_REMOVE);
        try{getSystemService(NotificationManager.class).notify(ID,base(FINISHED).setContentTitle("休息结束").setContentText("可以开始下一组训练了").setAutoCancel(true).build());}catch(SecurityException ignored){}
        stopSelf();
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
