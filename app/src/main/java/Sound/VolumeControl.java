package Sound;

/**
 * Smoothly adjusts the volume of an audio buffer to a desired volume based on its average volume and the previous average volume.
 */
public class VolumeControl {

    private static final double DESIRED_VOLUME = 0.2 * Short.MAX_VALUE;
    // Audio below this loudness threshold will be silenced.
    private static final double MINIMUM_VOLUME = 0.001 * Short.MAX_VALUE;
    // The volume calculated from the previous run of NormalizeVolume().+
    private static double previousVolume = DESIRED_VOLUME;
    private static boolean enabled = true;

    public static void ToggleEnabled() {
        enabled = !enabled;
    }

    public static void NormalizeVolume(short[] buffer) {
        if (!enabled)
            return;

        short[] maxVolume = {0};
        double averageVolume = GetAverageVolume(buffer, maxVolume);
        if (averageVolume < MINIMUM_VOLUME)
            averageVolume = 0;

        // Don't boost silence.
        double beginMultiplier = 0, endMultiplier = 0;
        if (previousVolume >= MINIMUM_VOLUME) beginMultiplier = DESIRED_VOLUME / previousVolume;
        if (averageVolume >= MINIMUM_VOLUME) endMultiplier = DESIRED_VOLUME / averageVolume;

        // Don't boost high enough to clip audio.
        if (maxVolume[0] * beginMultiplier > Short.MAX_VALUE)
            beginMultiplier = (double)Short.MAX_VALUE / maxVolume[0];
        if (maxVolume[0] * endMultiplier > Short.MAX_VALUE)
            endMultiplier = (double)Short.MAX_VALUE / maxVolume[0];

        AdjustVolume(buffer, beginMultiplier, endMultiplier);

        previousVolume = averageVolume;
    }

    // Calculates average volume of audio buffer using RMS (root mean square).
    // Also returns max volume via parameter.
    private static double GetAverageVolume(short[] buffer, short[] maxValue) {
        long sum = 0;
        for (short sample : buffer) {
            sum += (sample) * (sample);
            maxValue[0] = (short)Math.max(Math.abs(sample), maxValue[0]);
        }
        return Math.sqrt((double)sum / buffer.length);
    }

    // Adjusts volume of audio buffer samples linearly based on previous volume and current volume.
    private static void AdjustVolume(short[] buffer, double beginMultiplier, double endMultiplier) {
        // Linearly interpolate between the previous volume and the current buffer's volume.
        float[] interpolation = new float[buffer.length];
        for (int i = 0; i < buffer.length; i++)
            interpolation[i] = (float)i / buffer.length;

        double beginToEnd = endMultiplier - beginMultiplier;
        int newVolume;
        for (int i = 0; i < buffer.length; i++) {
            // Calculate new volume and clip value within the range of a short value.
            newVolume = (int)(buffer[i] * (beginMultiplier + (beginToEnd * interpolation[i])));
            buffer[i] = (short)(Math.max(Math.min(newVolume, Short.MAX_VALUE), Short.MIN_VALUE));
        }
    }
}