package app.rwun;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Presentation only: workout history and details share a readable metric hierarchy. */
final class ProgressViews {
  private ProgressViews() {}

  static String workoutType(String value) { return value.equals("Unstructured") ? "Free run" : value; }

  static String title(RunStore.Run run) {
    if (run.workoutType().equals("Unstructured")) {
      if (run.source().startsWith("Indoor")) return "Indoor run";
      return run.routeFile().isEmpty() ? "Run" : "Outdoor run";
    }
    return run.workoutType();
  }

  static String source(Context context, String raw) {
    if (raw.startsWith("Garmin ")) return "Garmin";
    if (raw.startsWith("Health Connect")) {
      String[] parts = raw.split(" · ", 2);
      if (parts.length == 2) {
        if (parts[1].equals("com.garmin.android.apps.connectmobile")) return "Garmin · Health Connect";
        if (parts[1].equals("com.strava")) return "Strava · Health Connect";
        try {
          var manager = context.getPackageManager();
          String label = manager.getApplicationLabel(manager.getApplicationInfo(parts[1], 0)).toString();
          if (!label.equals(parts[1])) return label + " · Health Connect";
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
      }
      return "Health Connect";
    }
    if (raw.equals("GPS")) return "OpenRun GPS";
    if (raw.startsWith("Indoor")) return "OpenRun indoor";
    return raw;
  }

  static String distance(double km) { return km > 0 ? String.format(Locale.getDefault(), "%.2f km", km) : "Unavailable"; }
  static String time(long seconds) {
    return seconds >= 3600
        ? String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
  }
  static String pace(RunStore.Run run) {
    return run.km() > 0 && run.seconds() > 0 ? PaceProfile.format(run.seconds() / run.km()) + " /km" : "—";
  }
  static String date(RunStore.Run run, long started) {
    LocalDate date = LocalDate.parse(run.date());
    String day = date.equals(LocalDate.now()) ? "Today" : date.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    return started <= 0 ? day : day + " · " + Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  static TextView label(Context c, String value, int sp, int color, boolean bold) {
    TextView text = UiKit.label(c, value, sp, color, bold);
    // Progress has its own type scale; avoid the app-wide compact scale shrinking it twice.
    text.setTextSize(sp);
    return text;
  }

  static void metrics(Context context, UiKit.Palette p, LinearLayout parent, String[] names, String[] values, boolean large) {
    // Two columns at accessibility sizes leave enough room for values to wrap without shrinking.
    int columns = context.getResources().getConfiguration().fontScale >= 1.3f ? 2 : 3;
    for (int start = 0; start < names.length; start += columns) {
      LinearLayout row = new LinearLayout(context);
      row.setBaselineAligned(false);
      for (int index = start; index < Math.min(start + columns, names.length); index++) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, UiKit.dp(context, 8), UiKit.dp(context, 8), UiKit.dp(context, 8));
        cell.addView(label(context, names[index], 12, p.muted, false));
        cell.addView(label(context, values[index], large ? 24 : 17, p.ink, true));
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
      }
      parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
  }

  static LinearLayout workout(Context c, UiKit.Palette p, RunStore.Run run, long started, Runnable open) {
    LinearLayout card = new LinearLayout(c);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(UiKit.dp(c, 16), UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 12));
    card.setBackground(UiKit.ripple(c, p.surface, 16));
    LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, -2);
    space.setMargins(0, UiKit.dp(c, 6), 0, UiKit.dp(c, 6));
    card.setLayoutParams(space);
    LinearLayout heading = new LinearLayout(c);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    ImageView icon = new ImageView(c);
    icon.setImageDrawable(UiKit.icon(c, run.sessionId().startsWith("walk:") ? R.drawable.ic_directions_walk : R.drawable.ic_directions_run, p.primary, 28));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams iconSpace = new LinearLayout.LayoutParams(UiKit.dp(c, 32), UiKit.dp(c, 40));
    iconSpace.setMargins(0, 0, UiKit.dp(c, 12), 0);
    heading.addView(icon, iconSpace);
    LinearLayout title = new LinearLayout(c);
    title.setOrientation(LinearLayout.VERTICAL);
    title.addView(label(c, title(run), 19, p.ink, true));
    title.addView(label(c, date(run, started), 13, p.muted, false));
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView chevron = new ImageView(c);
    chevron.setImageDrawable(UiKit.icon(c, R.drawable.ic_chevron_right, p.muted, 20));
    chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    heading.addView(chevron, new LinearLayout.LayoutParams(UiKit.dp(c, 24), UiKit.dp(c, 24)));
    card.addView(heading);
    metrics(c, p, card, new String[]{"Distance", "Time", "Avg pace"},
        new String[]{distance(run.km()), time(run.seconds()), pace(run)}, false);
    card.addView(label(c, source(c, run.source()) + (run.routeFile().isEmpty() ? "" : " · GPS route"), 12, p.muted, false));
    card.setMinimumHeight(UiKit.dp(c, 48));
    card.setClickable(true);
    card.setFocusable(true);
    card.setOnClickListener(v -> { UiKit.feedback(v); open.run(); });
    return card;
  }
}
