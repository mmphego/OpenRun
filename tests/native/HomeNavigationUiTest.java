package app.rwun;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Native Android host renders and interactions, not physical-device acceptance. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w360dp-h780dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HomeNavigationUiTest {
  RunStore store;

  @Before public void setup() {
    Application app=RuntimeEnvironment.getApplication();
    app.getSharedPreferences("rwun",0).edit().clear().commit();
    store=new RunStore(app);
    TrackingService service=Robolectric.buildService(TrackingService.class).create().get();
    Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
        new ComponentName(app,TrackingService.class),service.onBind(new Intent()));
  }

  private View navigation(MainActivity activity,String title) {
    View item=findNavigation(activity.getWindow().getDecorView(),title);
    assertNotNull("Missing destination: "+title,item);
    assertTrue(item.isClickable());
    assertTrue(item.isFocusable());
    assertTrue(item.getHeight()>=48);
    assertTrue(((ViewGroup)item).getChildAt(0) instanceof ImageView);
    assertNotNull(((ImageView)((ViewGroup)item).getChildAt(0)).getDrawable());
    return item;
  }

  private View findNavigation(View view,String title) {
    CharSequence description=view.getContentDescription();
    if(view.isClickable()&&description!=null&&
        (description.toString().equals(title)||description.toString().equals(title+", selected")))return view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
      View found=findNavigation(((ViewGroup)view).getChildAt(i),title);
      if(found!=null)return found;
    }
    return null;
  }

  private ScrollView layout(MainActivity activity,int width,int height,String name) throws Exception {
    View decor=activity.getWindow().getDecorView();
    View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
    root.dispatchApplyWindowInsets(new WindowInsets.Builder().setSystemWindowInsets(android.graphics.Insets.of(0,24,0,24)).build());
    decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
    decor.layout(0,0,width,height);
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    decor.draw(new Canvas(bitmap));
    File file=new File(System.getProperty("rwun.evidence"),name+".png");
    file.getParentFile().mkdirs();
    try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    bitmap.recycle();
    return (ScrollView)((ViewGroup)root).getChildAt(1);
  }

  @Test public void noPlanHomeShowsIconsAndActionsWithoutScrolling() throws Exception {
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity=controller.get();
      ScrollView scroll=layout(activity,360,780,"home-no-plan-compact");
      for(String title:new String[]{"Home","Plan","Calendar","Progress","Coach"})navigation(activity,title);
      assertTrue(navigation(activity,"Home").isSelected());
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Movement reminders"));
      assertTrue("Home actions should fit at default font size",scroll.getChildAt(0).getHeight()<=scroll.getHeight());
    }
  }

  @Test public void homeExitsSetupAndSurvivesRecreation() throws Exception {
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity=controller.get();
      NativePlanningTest.click(activity,"Plan");
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"What are you training for?"));
      NativePlanningTest.click(activity,"Home");
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Start a walk"));
      controller.recreate();activity=controller.get();layout(activity,360,780,"home-after-recreation");
      assertTrue(navigation(activity,"Home").isSelected());
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Create my plan"));
      assertNull(store.config());
    }
  }

  @Test public void noPlanDestinationsReturnHomeWithoutToolbar() throws Exception {
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity=controller.get();
      for(String title:new String[]{"Calendar","Progress","Coach"}) {
        NativePlanningTest.click(activity,title);layout(activity,360,780,"no-plan-"+title.toLowerCase());
        assertTrue(navigation(activity,title).isSelected());
        NativePlanningTest.click(activity,"Home");
        assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Start a walk"));
      }
    }
  }

  @Test public void largeFontsKeepNavigationFixedAndActionsReachable() throws Exception {
    RuntimeEnvironment.setFontScale(1.6f);
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity=controller.get();
      ScrollView scroll=layout(activity,320,640,"home-large-font-small-screen");
      View home=navigation(activity,"Home");int[] before=new int[2];home.getLocationOnScreen(before);
      scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
      int[] after=new int[2];home.getLocationOnScreen(after);assertArrayEquals(before,after);
      Rect visible=new Rect();
      assertTrue(NativePlanningTest.find(activity.getWindow().getDecorView(),"Movement reminders").getGlobalVisibleRect(visible));
      assertTrue(home.getGlobalVisibleRect(visible));
    } finally {RuntimeEnvironment.setFontScale(1f);}
  }

  @Test public void existingPlanHubFitsAndHomePreservesPlan() throws Exception {
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
    String planId=store.planId();
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Plan");
      ScrollView scroll=layout(activity,360,780,"plan-hub-compact");
      assertTrue("Plan hub actions should fit",scroll.getChildAt(0).getHeight()<=scroll.getHeight());
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Continue plan"));
      NativePlanningTest.click(activity,"Home");
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Your training, today."));
      assertEquals(planId,store.planId());
    }
  }
}
