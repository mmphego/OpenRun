package app.rwun;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.List;

/** Offline route sketch; intentionally not a street map. */
final class RouteView extends View {
  private final List<ActivityFiles.Sample> points;
  private final Path path = new Path();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public RouteView(Context c) {
    this(c, java.util.Collections.emptyList());
  }

  RouteView(Context c, List<ActivityFiles.Sample> points) {
    super(c);
    this.points = points;
    setContentDescription("Recorded GPS route. Offline line sketch without map tiles.");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (points.size() < 2) return;
    double minX = 180, maxX = -180, minY = 90, maxY = -90;
    double latitude = points.get(0).lat();
    double cos = Math.cos(Math.toRadians(latitude));
    for (var p : points) {
      double x = p.lon() * cos;
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, p.lat());
      maxY = Math.max(maxY, p.lat());
    }
    double spanX = Math.max(.00001, maxX - minX), spanY = Math.max(.00001, maxY - minY);
    double scale = Math.min((getWidth() - 40) / spanX, (getHeight() - 40) / spanY);
    paint.setColor(new UiKit.Palette(true).primary);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(6);
    path.reset();
    int segment = -1;
    for (var p : points) {
      float x = (float) (20 + (p.lon() * cos - minX) * scale),
          y = (float) (getHeight() - 20 - (p.lat() - minY) * scale);
      if (segment != p.segment()) {
        path.moveTo(x, y);
        segment = p.segment();
      } else path.lineTo(x, y);
    }
    canvas.drawPath(path, paint);
  }
}
