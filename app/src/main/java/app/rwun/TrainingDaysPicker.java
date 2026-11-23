package app.rwun;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/** Native weekday controls shared by plan creation and schedule editing. */
final class TrainingDaysPicker extends LinearLayout {
  private final UiKit.Palette palette;
  private final EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
  private final EnumMap<DayOfWeek, Button> buttons = new EnumMap<>(DayOfWeek.class);
  private final BiConsumer<Set<DayOfWeek>, DayOfWeek> onChange;
  private final TextView count;
  private final Button longRun;
  private DayOfWeek longDay;

  TrainingDaysPicker(Activity activity, UiKit.Palette palette, Set<DayOfWeek> selected,
      DayOfWeek longDay, BiConsumer<Set<DayOfWeek>, DayOfWeek> onChange) {
    super(activity);
    this.palette = palette;
    this.onChange = onChange;
    this.longDay = longDay;
    days.addAll(selected);
    setOrientation(VERTICAL);
    addView(UiKit.label(activity, "Training days", 18, palette.ink, true));
    count = UiKit.label(activity, "", 14, palette.muted, false);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    addView(count);
    PlanningUi.gap(activity, this, 12);
    DayOfWeek[] weekdays = DayOfWeek.values();
    for (int offset = 0; offset < weekdays.length; offset += 3) {
      var row = new LinearLayout(activity);
      for (int column = 0; column < 3; column++) {
        var params = new LinearLayout.LayoutParams(0, -2, 1);
        if (offset + column >= weekdays.length) {
          row.addView(new View(activity), params);
          continue;
        }
        DayOfWeek day = weekdays[offset + column];
        Button button = PlanningUi.chip(activity, day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            days.contains(day), () -> toggle(day));
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(dayName(day));
        params.setMarginEnd(PlanningUi.dp(activity, 6));
        row.addView(button, params);
        buttons.put(day, button);
      }
      addView(row);
      PlanningUi.gap(activity, this, 6);
    }
    TextView longRunLabel = UiKit.label(activity, "Long-run day", 15, palette.ink, true);
    addView(longRunLabel);
    longRun = UiKit.action(activity, palette, "", this::chooseLongDay);
    longRun.setId(View.generateViewId());
    longRunLabel.setLabelFor(longRun.getId());
    addView(longRun);
    refresh();
  }

  private void toggle(DayOfWeek day) {
    if (!days.remove(day)) days.add(day);
    if (!days.isEmpty() && !days.contains(longDay)) longDay = days.iterator().next();
    publishChange();
  }

  private void publishChange() {
    refresh();
    onChange.accept(Set.copyOf(days), longDay);
  }

  private void refresh() {
    for (var entry : buttons.entrySet()) {
      Button button = entry.getValue();
      boolean selected = days.contains(entry.getKey());
      button.setSelected(selected);
      button.setTextColor(selected ? palette.primary : palette.ink);
      var background = UiKit.shape(getContext(), selected ? palette.tonal : palette.background, 12);
      if (selected) background.setStroke(PlanningUi.dp(getContext(), 1), palette.primary);
      button.setBackground(background);
      if (android.os.Build.VERSION.SDK_INT >= 30)
        button.setStateDescription(selected ? "Selected" : "Available");
    }
    count.setText(days.isEmpty() ? "Choose at least one day" : days.size() == 1
        ? "1 day per week" : days.size() + " days per week");
    longRun.setText(days.isEmpty() ? "Select a training day first" : dayName(longDay));
    longRun.setEnabled(days.size() > 1);
  }

  private void chooseLongDay() {
    List<DayOfWeek> choices = List.copyOf(days);
    String[] labels = choices.stream().map(TrainingDaysPicker::dayName).toArray(String[]::new);
    new AlertDialog.Builder(getContext()).setTitle("Long-run day")
        .setSingleChoiceItems(labels, choices.indexOf(longDay), (dialog, which) -> {
          longDay = choices.get(which);
          publishChange();
          dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
  }

  private static String dayName(DayOfWeek day) {
    return day.getDisplayName(TextStyle.FULL, Locale.getDefault());
  }
}
