package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;

/** Run on an emulator with notifications allowed; does not change training records. */
public final class TimerTestRunner extends Instrumentation {
    void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    Notification notification(Context c){for(StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==RestTimerService.ID)return n.getNotification();return null;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Activity activity=null;try{
        check(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED,"allow notifications before test");
        activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->c.startForegroundService(new Intent(c,RestTimerService.class).putExtra("end",System.currentTimeMillis()+60000).putExtra("session",3)));
        Thread.sleep(1000);
        Notification running=notification(c);check(running!=null,"running notification exists");check(running.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),"system chronometer enabled");check(running.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),"chronometer counts down");check(running.actions.length==1,"stop action exists");
        runOnMainSync(()->c.startForegroundService(new Intent(c,RestTimerService.class).putExtra("end",System.currentTimeMillis()+90000).putExtra("session",3)));
        Thread.sleep(500);check(RestTimerService.prefs(c).getLong("timerEnd",0)>System.currentTimeMillis()+80000,"replacement timer persisted");
        notification(c).actions[0].actionIntent.send();Thread.sleep(500);check(notification(c)==null,"stop action removes notification");check(!RestTimerService.prefs(c).contains("timerEnd"),"stop action clears app timer");
        runOnMainSync(()->c.startForegroundService(new Intent(c,RestTimerService.class).putExtra("end",System.currentTimeMillis()+1500).putExtra("session",3)));
        Activity opened=activity;runOnMainSync(()->opened.moveTaskToBack(true));Thread.sleep(2500);
        Notification done=notification(c);check(done!=null&&"休息结束".contentEquals(done.extras.getCharSequence(Notification.EXTRA_TITLE)),"background expiry posts completion");check((done.flags&Notification.FLAG_ONGOING_EVENT)==0,"completion is dismissible");check(!RestTimerService.prefs(c).contains("timerEnd"),"expiry clears app timer");
        done.contentIntent.send();Thread.sleep(500);check(RestTimerService.prefs(c).getInt("timerSession",-1)==3,"session preserved");
        result.putString("stream","PASS: countdown notification, replacement, stop action, shared state, background completion and return action.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{c.stopService(new Intent(c,RestTimerService.class));RestTimerService.prefs(c).edit().remove("timerEnd").apply();c.getSystemService(NotificationManager.class).cancel(RestTimerService.ID);}}
}
