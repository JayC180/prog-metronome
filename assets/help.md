### Beat
The numpad enters beat values. The ÷N button shows the current subdivision for the next input. For example, ÷4 matches
"16th notes" in classical western theory, then entering 1 onthe numpad will give you an item with duration of 1 16th note in relation to the bpm. Entering 3 is equivalent to a dotted 8th note. For anything above 9, press the "custom" button at the bottom left and enter the desired value. A beat can be turned on/off, its volume and sound adjusted in the editor above the numpad.

### Subdivision
The default subdivision is /4. To change it, click the ÷N button then press the numpad orcustom" for any value larger than 9. This gives easy access to all tuplets. For nested tuplets you need to do the calculation. Example: a triplet out of two notes from triplet-eighth-notes is 2/9 of the pulse, so set subdivision to 9 then press 2.

### Brackets and Repeats
[ and ] wrap a section. Press xN after the closing bracket then click the numpad to repeat that many times, or "custom" for more than 9. Infinite repeat is also an option. Useful for simulating meters, hypermeters, or any repeating pattern. Brackets can be nested. Example: [ 3 3 2 ]x4 [ 5 ]xinf plays a 3+3+2 group four times, then loops 5 indefinitely.

### Editing
The pencil button in the numpad is used for editing. When an item is selected, click pencil to enter edit mode -- number inputs will change the selected item instead of appending. ⌫ deletes the selected item, or the last item if nothing is selected. Paired bracket delete (in settings) removes both brackets together when you delete either one.

### Tempo Changes
mm inserts a metric modulation. After clicking it a popup prompts for two numbers. For example at 120 bpm, mm of x3/2 gives 180. =bpm sets an absolute tempo at that point. Both take effect at that position during playback and won't affect the global bpm. You cannot put mm inside an infinitely-repeated group.

### Tracks
Each track is a row. Multiple tracks play simultaneously, useful for polyrhythm and polymeters. Tap a track to select it and edit. Each track repeats from the start when the end is reached (except when there is an infinite repeat).

### Mute, Solo, Default Sound
Each track row has M (mute), S (solo), and snd (default sound/volume) chips. The sound chip opens a picker to set the default sound and volume for new beats on that track; the chip highlights when a custom sound is configured. Item-level sound/volume overrides the track default, which overrides the global default (in the settings menu).

### Projects
Project auto-saves. On relaunch you'll be offered to restore your last session. Use Save As in settings for named projects. Projects can be exported as .rhy files.

### Donation
If you have the ability, consider supporting the developer at [https://ko-fi.com/prog_metronome](https://ko-fi.com/prog_metronome). All the donations will *only* go to the annual Apple developer fees of $99 so I can keep publishing this app for IOS and MacOS.