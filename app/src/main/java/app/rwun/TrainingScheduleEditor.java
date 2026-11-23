package app.rwun;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Edits a draft of the existing schedule and saves only after review. */
final class TrainingScheduleEditor {
  private final Activity activity;
  private final RunStore store;
  private final UiKit.Palette palette;
  private final String fingerprint;
  private final EnumSet<DayOfWeek> days;
  private final EnumMap<DayOfWeek, Button> chips = new EnumMap<>(DayOfWeek.class);
  private DayOfWeek longDay;
  private Button longDayButton;
  private Button reviewButton;
  private TextView count;
  private EditText volume;
  private Spinner programme;

  TrainingScheduleEditor(Activity activity, RunStore store, UiKit.Palette palette, Bundle draft) {
    this.activity = activity;
    this.store = store;
    this.palette = palette;
    var saved = store.trainingPreferences();
    fingerprint = draft == null ? store.proposalFingerprint() : draft.getString("fingerprint");
    days = EnumSet.copyOf(saved.weekdays());
    longDay = saved.longDay();
    if (draft != null) {
      days.clear();
      for (int day : draft.getIntArray("days")) days.add(DayOfWeek.of(day));
      longDay = DayOfWeek.of(draft.getInt("longDay"));
    }
  }

  void show(LinearLayout body, TextView status, Bundle draft) {
    body.addView(UiKit.label(activity, "Your weekly schedule", 25, palette.ink, true));
    body.addView(UiKit.label(activity,
        "Applies to upcoming full weeks. Weeks with logged, moved, skipped or adjusted workouts stay as scheduled.",
        15, palette.muted, false));
    PlanningUi.gap(activity, body, 16);

    LinearLayout availability = PlanningUi.card(activity);
    availability.addView(PlanningUi.label(activity, "Training days", 18, true));
    count = PlanningUi.label(activity, "", 14, false);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    availability.addView(count);
    PlanningUi.gap(activity, availability, 12);
    LinearLayout row = null;
    for (DayOfWeek day : DayOfWeek.values()) {
      if ((day.getValue() - 1) % 3 == 0) {
        row = new LinearLayout(activity);
        availability.addView(row);
        PlanningUi.gap(activity, availability, 6);
      }
      Button chip = PlanningUi.chip(activity, day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
          days.contains(day), () -> toggle(day));
      chip.setPadding(0, 0, 0, 0);
      var layout = new LinearLayout.LayoutParams(0, -2, 1);
      layout.setMarginEnd(PlanningUi.dp(activity, 6));
      row.addView(chip, layout);
      chips.put(day, chip);
      if (day == DayOfWeek.SUNDAY) row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 2));
    }
    PlanningUi.gap(activity, availability, 8);
    availability.addView(PlanningUi.label(activity, "Long-run day", 15, true));
    longDayButton = UiKit.action(activity, palette, "", this::chooseLongDay);
    availability.addView(longDayButton);
    body.addView(availability);

    LinearLayout settings = PlanningUi.card(activity);
    settings.addView(PlanningUi.label(activity, "Training load", 18, true));
    TextView volumeLabel = PlanningUi.label(activity, "Starting weekly distance (km)", 14, false);
    settings.addView(volumeLabel);
    volume = new EditText(activity);
    volume.setId(View.generateViewId());
    volumeLabel.setLabelFor(volume.getId());
    volume.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    volume.setSingleLine(true);
    volume.setTextColor(palette.ink);
    volume.setText(draft == null ? Float.toString((float) store.config().weeklyKm())
        : draft.getString("volume"));
    settings.addView(volume);

    TextView programmeLabel = PlanningUi.label(activity, "Programme", 14, false);
    settings.addView(programmeLabel);
    programme = new Spinner(activity);
    programme.setId(View.generateViewId());
    programmeLabel.setLabelFor(programme.getId());
    programme.setMinimumHeight(PlanningUi.dp(activity, 48));
    programme.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
        PlanPreferences.MODES));
    programme.setSelection(draft == null ? PlanPreferences.MODES.indexOf(store.trainingPreferences().mode())
        : draft.getInt("programme"));
    settings.addView(programme);
    body.addView(settings);

    reviewButton = UiKit.action(activity, palette, "Review schedule", () -> review(status));
    reviewButton.setBackground(UiKit.ripple(activity, palette.primary, 18));
    reviewButton.setTextColor(palette.background);
    reviewButton.setCompoundDrawablesRelative(null, null, null, null);
    body.addView(reviewButton);
    refreshDays();
  }

  Bundle draft() {
    Bundle draft = new Bundle();
    draft.putString("fingerprint", fingerprint);
    draft.putIntArray("days", days.stream().mapToInt(DayOfWeek::getValue).toArray());
    draft.putInt("longDay", longDay.getValue());
    draft.putString("volume", volume.getText().toString());
    draft.putInt("programme", programme.getSelectedItemPosition());
    return draft;
  }

  private void toggle(DayOfWeek day) {
    if (!days.remove(day)) days.add(day);
    if (!days.isEmpty() && !days.contains(longDay)) longDay = days.iterator().next();
    refreshDays();
  }

  private static String dayName(DayOfWeek day) {
    return day.getDisplayName(TextStyle.FULL, Locale.getDefault());
  }

  private void refreshDays() {
    for (var entry : chips.entrySet()) {
      DayOfWeek day = entry.getKey();
      Button chip = entry.getValue();
      boolean selected = days.contains(day);
      chip.setSelected(selected);
      chip.setTextColor(selected ? palette.primary : palette.ink);
      var background = PlanningUi.shape(activity, selected ? 0xff0d3632 : palette.background, 12, true);
      if (selected) background.setStroke(PlanningUi.dp(activity, 1), palette.primary);
      chip.setBackground(background);
      chip.setContentDescription(dayName(day));
      if (android.os.Build.VERSION.SDK_INT >= 30)
        chip.setStateDescription(selected ? "Selected" : "Available");
    }
    count.setText(days.isEmpty() ? "Choose at least one day" : days.size() == 1 ? "1 day per week · shorter weekly distance"
        : days.size() + " days per week");
    longDayButton.setText(days.isEmpty() ? "Select a training day first" : dayName(longDay));
    longDayButton.setEnabled(days.size() > 1);
    reviewButton.setEnabled(!days.isEmpty());
    reviewButton.setAlpha(days.isEmpty() ? .5f : 1f);
  }

  private void chooseLongDay() {
    List<DayOfWeek> choices = List.copyOf(days);
    String[] labels = choices.stream().map(TrainingScheduleEditor::dayName).toArray(String[]::new);
    new AlertDialog.Builder(activity).setTitle("Long-run day")
        .setSingleChoiceItems(labels, choices.indexOf(longDay), (dialog, which) -> {
          longDay = choices.get(which);
          refreshDays();
          dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
  }

  private void showError(TextView status, RuntimeException error) {
    status.setText(UserErrors.message(error));
    status.post(() -> status.requestRectangleOnScreen(
        new android.graphics.Rect(0, 0, status.getWidth(), status.getHeight()), true));
  }

  private void review(TextView status) {
    try {
      double weeklyKm = Double.parseDouble(volume.getText().toString().trim().replace(',', '.'));
      var preferences = new PlanPreferences(programme.getSelectedItem().toString(), days, longDay);
      var review = store.reviewTrainingSchedule(fingerprint, preferences, weeklyKm);
      status.setText("");
      DateTimeFormatter date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault());
      LinearLayout details = PlanningUi.column(activity);
      int padding = PlanningUi.dp(activity, 24);
      details.setPadding(padding, 0, padding, padding / 2);
      details.addView(UiKit.label(activity, "First updated week · " + review.weekStart().format(date),
          15, palette.primary, true));
      for (var workout : review.workouts()) {
        PlanningUi.gap(activity, details, 10);
        details.addView(UiKit.label(activity, workout.date().format(date) + " · "
            + PlanEngine.distance(workout.km()), 15, palette.ink, true));
        details.addView(UiKit.label(activity, workout.title(), 14, palette.muted, false));
      }
      ScrollView scroll = new ScrollView(activity);
      scroll.addView(details);
      AlertDialog confirmation = new AlertDialog.Builder(activity).setTitle("Review schedule").setView(scroll)
          .setNegativeButton("Keep editing", null)
          .setPositiveButton("Save changes", (dialog, which) -> {
            try {
              store.saveTrainingSchedule(review);
              Reminders.schedule(activity);
              WorkoutWidget.updateAll(activity);
              Toast.makeText(activity, "Training schedule updated", Toast.LENGTH_SHORT).show();
              activity.finish();
            } catch (RuntimeException e) { showError(status, e); }
          }).show();
      confirmation.getWindow().setBackgroundDrawable(UiKit.shape(activity, palette.surface, 22));
      confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
      confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
    } catch (NumberFormatException e) {
      volume.setError("Enter a weekly distance from 4 to 120 km");
      volume.requestFocus();
    } catch (RuntimeException e) { showError(status, e); }
  }
}
