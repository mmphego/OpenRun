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
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;

/** Edits a draft of the existing schedule and saves only after review. */
final class TrainingScheduleEditor {
  static final String UPDATED_WEEK = "updatedTrainingWeek";
  private final Activity activity;
  private final RunStore store;
  private final UiKit.Palette palette;
  private final String fingerprint;
  private final EnumSet<DayOfWeek> days;
  private DayOfWeek longDay;
  private Button reviewButton;
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
    availability.addView(new TrainingDaysPicker(activity, palette, days, longDay, (chosen, longest) -> {
      days.clear();
      days.addAll(chosen);
      longDay = longest;
      updateReviewAvailability();
    }));
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
    updateReviewAvailability();
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

  private void updateReviewAvailability() {
    reviewButton.setEnabled(!days.isEmpty());
    reviewButton.setAlpha(days.isEmpty() ? .5f : 1f);
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
              activity.setResult(android.app.Activity.RESULT_OK, new android.content.Intent()
                  .putExtra(UPDATED_WEEK, review.weekStart().toString()));
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
