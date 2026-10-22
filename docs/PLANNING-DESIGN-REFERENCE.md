# Planning visual target

References: user screenshots 1000517452–1000517464. Keep OpenRun branding and running/walking scope.

- A returning-user Plan screen with three full-width actions: Start new plan, Restart current plan, Continue plan.
- Starting a plan opens a fullscreen black wizard with back, compact progress track and close. No duplicate app toolbar or bottom navigation in the wizard.
- First page asks the goal with substantial illustrated/icon cards: Race, specific distance, start running, return, 5 km improvement, general training. Do not add removed multisport or clinical plans.
- Race search has a single search bar and a horizontal row of Dates/Distance/Country chips, not a column of spinners and date buttons.
- Race cards use original city imagery, subtle dark overlay and teal date/distance text. No invented popularity counts. One card tap continues. A manual-add card sits within the list.
- Manual form uses large rounded field containers, a date picker, country/distance selection sheets and a bottom primary button enabled only for valid input.
- Subsequent fitness/days/preview screens share the same visual shell and concise primary action. Detailed explanations are collapsed or shown in a sheet.

Visual verification must inspect the actual Android Views. This release uses Robolectric Native Graphics host renders and interaction checks; these are not emulator/S23 screenshots. A successful build alone is not a visual pass. Design-canvas CLI remains blocked after its earlier unrequested telemetry auto-review rejection; native implementation and emulator QA are the available route.
