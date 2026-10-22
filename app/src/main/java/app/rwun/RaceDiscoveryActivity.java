package app.rwun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Local-only catalogue reads; JobScheduler owns all provider network requests. */
public final class RaceDiscoveryActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final UiKit.Palette palette = new UiKit.Palette(true);
    private final Runnable observer = () -> runOnUiThread(this::load);
    private RaceRepository repository;
    private LinearLayout results;
    private TextView status;
    private EditText query;
    private CheckBox savedOnly;
    private int offset, generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = RaceRepository.get(this);
        LinearLayout body = PlanningUi.column(this);
        body.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 36), UiKit.dp(this, 20), UiKit.dp(this, 24));
        body.setBackgroundColor(PlanningUi.CANVAS);
        body.addView(PlanningUi.label(this, "Find your race", 30, true));
        body.addView(PlanningUi.label(this, "Cached listings · confirm details with the organiser", 15, false));
        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Race name, city or venue");
        query.setContentDescription("Search local race catalogue");
        if (state != null) query.setText(state.getString("query", ""));
        body.addView(query);
        savedOnly = new CheckBox(this);
        savedOnly.setText("Saved races only");
        savedOnly.setChecked(state != null && state.getBoolean("saved"));
        body.addView(savedOnly);
        body.addView(UiKit.action(this, palette, "Search", () -> { offset = 0; load(); }));
        body.addView(UiKit.action(this, palette, "Refresh in background", () -> {
            RaceSyncJob.schedule(this);
            Toast.makeText(this, "Update queued. Cached races remain available.", Toast.LENGTH_SHORT).show();
        }));
        body.addView(UiKit.action(this, palette, "Add a race manually", () -> startActivity(new Intent(this, RacePlanActivity.class).putExtra("mode", "manual"))));
        status = PlanningUi.label(this, "Loading local catalogue…", 13, false);
        body.addView(status);
        results = PlanningUi.column(this);
        body.addView(results);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
        repository.observe(observer);
        load();
    }

    private void load() {
        if (isFinishing() || isDestroyed()) return;
        int request = ++generation;
        String search = query.getText().toString();
        boolean onlySaved = savedOnly.isChecked();
        int page = offset;
        submit(() -> {
            try {
                repository.seed(this);
                List<RaceDatabase.Card> cards = repository.search(new RaceRepository.Filter(search, "", "", "All", 0,
                        LocalDate.now().toString(), LocalDate.now().plusYears(5).toString(), onlySaved, page));
                StringBuilder summary = new StringBuilder("Provider status: ");
                for (var state : repository.database().catalogue().states()) {
                    summary.append(state.provider).append(": ").append(state.error.isBlank() ? (state.success == 0 ? "pending" : "cached") : state.error).append(". ");
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    status.setText(summary.toString());
                    results.removeAllViews();
                    if (cards.isEmpty()) results.addView(PlanningUi.label(this, "No matching cached races. Try another search or add one manually.", 16, false));
                    for (var card : cards.subList(0, Math.min(30, cards.size()))) {
                        LinearLayout row = PlanningUi.card(this);
                        row.addView(PlanningUi.label(this, card.name, 20, true));
                        row.addView(PlanningUi.label(this, card.date + " · " + card.city + " · " + RaceData.countryName(card.country) + " · " + card.surface, 14, false));
                        row.addView(UiKit.action(this, palette, "Distances and sources", () -> details(card.id)));
                        row.addView(UiKit.action(this, palette, card.saved ? "Unsave race" : "Save race", () -> submit(() -> {
                            if (card.saved) repository.unsave(card.id); else repository.save(card.id, null);
                        })));
                        results.addView(row);
                    }
                    if (page > 0) results.addView(UiKit.action(this, palette, "Previous page", () -> { offset = Math.max(0, offset - 30); load(); }));
                    if (cards.size() > 30) results.addView(UiKit.action(this, palette, "Next page", () -> { offset += 30; load(); }));
                });
            } catch (Exception e) { report(e); }
        });
    }

    private void details(String id) {
        submit(() -> {
            try {
                var distances = repository.options(id).stream().filter(d -> d.available).collect(java.util.stream.Collectors.toList());
                var sources = repository.references(id);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    String[] labels = new String[distances.size() + sources.size()];
                    for (int i = 0; i < distances.size(); i++) labels[i] = "Plan · " + distances.get(i).label + " · " + distances.get(i).surface;
                    for (int i = 0; i < sources.size(); i++) labels[distances.size() + i] = "Source · " + sources.get(i).provider;
                    new AlertDialog.Builder(this).setTitle(distances.isEmpty() ? "Distance unknown · check organiser" : "Choose distance or source")
                        .setItems(labels, (dialog, index) -> {
                            if (index < distances.size()) {
                                String distanceId = distances.get(index).id;
                                submit(() -> {
                                    try {
                                        String race = repository.planningRace(id, distanceId).toString();
                                        runOnUiThread(() -> { if (!isDestroyed()) startActivity(new Intent(this, RacePlanActivity.class).putExtra("raceJson", race)); });
                                    } catch (Exception e) { report(e); }
                                });
                            } else {
                                String url = RaceData.url(sources.get(index - distances.size()).sourceUrl);
                                if (!url.isBlank()) try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { report(e); }
                            }
                        }).setNegativeButton("Close", null).show();
                });
            } catch (Exception e) { report(e); }
        });
    }

    private void submit(Runnable task) {
        try { worker.execute(() -> { try { task.run(); } catch (Exception e) { report(e); } }); }
        catch (RejectedExecutionException ignored) { /* Activity already closed. */ }
    }
    private void report(Exception e) { runOnUiThread(() -> { if (!isDestroyed()) status.setText(UserErrors.message(e)); }); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("query", query.getText().toString());
        out.putBoolean("saved", savedOnly.isChecked());
    }
    @Override protected void onDestroy() {
        if (repository != null) repository.removeObserver(observer);
        worker.shutdownNow();
        super.onDestroy();
    }
}
