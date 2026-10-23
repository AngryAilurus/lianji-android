package cn.lianji.training;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A bounded, silent celebration that follows the system animation setting. */
final class CompletionView extends View {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path check=new Path(),segment=new Path();
    ValueAnimator animator;
    float progress=1;
    CompletionView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();
        if(!ValueAnimator.areAnimatorsEnabled()){progress=1;invalidate();return;}
        progress=0;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(1400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;}super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(43),reveal=Math.min(1,progress*2);
        paint.setStyle(Paint.Style.FILL);paint.setColor(MainActivity.PALE);canvas.drawCircle(cx,cy,r+dp(9)*reveal,paint);
        paint.setColor(MainActivity.GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(4));
        canvas.drawArc(cx-r,cy-r,cx+r,cy+r,-90,360*reveal,false,paint);
        check.reset();check.moveTo(cx-dp(18),cy);check.lineTo(cx-dp(4),cy+dp(14));check.lineTo(cx+dp(23),cy-dp(15));
        segment.reset();new PathMeasure(check,false).getSegment(0,new PathMeasure(check,false).getLength()*Math.max(0,Math.min(1,(progress-.18f)*2)),segment,true);canvas.drawPath(segment,paint);
        if(progress>0&&progress<1){
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<18;i++){double angle=i*Math.PI*2/18;float distance=dp(57+43*progress),x=cx+(float)Math.cos(angle)*distance,y=cy+(float)Math.sin(angle)*distance+dp(12)*progress*progress;
                paint.setColor(i%3==0?0xff58b7e8:i%3==1?MainActivity.GREEN:0xff9cb5fa);paint.setAlpha((int)(255*(1-progress)));
                canvas.save();canvas.rotate(i*27+progress*100,x,y);canvas.drawRoundRect(x-dp(2),y-dp(4),x+dp(2),y+dp(4),dp(1),dp(1),paint);canvas.restore();
            }
        }
        paint.setAlpha(255);
    }
}
