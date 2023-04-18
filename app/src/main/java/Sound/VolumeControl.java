package Sound;

/**
 * Smoothly adjusts the volume of an audio buffer to a desired volume based on its average volume and the previous average volume.
 * How to use: call SetSampleRate() once -> Write new audio samples to buffer ->
 * call NormalizeVolume() every time the buffer is filled -> Read modified audio samples from buffer.
 */
public class VolumeControl {

    // Length of buffer in seconds.
    private static final double BUFFER_DURATION = 0.05;
    // Basically this will sound half as loud as a square wave.
    private static final double DESIRED_VOLUME = 0.1 * Short.MAX_VALUE;

    // The volume calculated from the previous run of NormalizeVolume().
    private static double previousVolume = DESIRED_VOLUME;
    // Audio samples are usually 16 bits stored in two's complement, so use shorts.
    public static short[] buffer;
    // Pre-calculate 0-1 interpolation values when sample rate is set.
    private static float[] interpolation;

    public static void SetSampleRate(int inputSampleRate) {
        // Number of audio samples per second. Usually 44100Hz or 48000Hz.
        int bufferLength = (int) (inputSampleRate * BUFFER_DURATION);
        buffer = new short[bufferLength];

        // Pre-calculate 0-1 interpolation values.
        interpolation = new float[bufferLength];
        for (int i = 0; i < inputSampleRate; i++)
            interpolation[i] = (float)i / inputSampleRate;
    }

    public static void NormalizeVolume() {
        double averageVolume = GetAverageVolume();

        // Don't boost silence.
        double beginMultiplier = 0, endMultiplier = 0;
        if (previousVolume != 0) beginMultiplier = DESIRED_VOLUME / previousVolume;
        if (averageVolume != 0) endMultiplier = DESIRED_VOLUME / averageVolume;

        AdjustVolume(beginMultiplier, endMultiplier);

        previousVolume = averageVolume;
    }

    // Calculates volume of audio buffer using RMS (root mean square).
    private static double GetAverageVolume() {
        long sum = 0;
        for (short sample : buffer)
            sum += (sample) * (sample);
        return Math.sqrt((double)sum / buffer.length);
    }

    // Adjusts volume of audio buffer samples linearly based on previous volume and current volume.
    private static void AdjustVolume(double beginMultiplier, double endMultiplier) {
        double beginToEnd = endMultiplier - beginMultiplier;
        int newVolume;
        for (int i = 0; i < buffer.length; i++) {
            // Calculate new volume and clip value within the range of a short value.
            newVolume = (int)(buffer[i] * (beginMultiplier + (beginToEnd * interpolation[i])));
            buffer[i] = (short)(Math.max(Math.min(newVolume, Short.MAX_VALUE), Short.MIN_VALUE));
        }
    }
}