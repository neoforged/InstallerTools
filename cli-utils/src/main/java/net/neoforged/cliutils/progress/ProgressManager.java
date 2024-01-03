package net.neoforged.cliutils.progress;

/**
 * A manager that changes the progress of a progress bar, to indicate the current progress to users.
 */
public interface ProgressManager {
    /**
     * Sets the max progress of the manager.
     */
    void setMaxProgress(int maxProgress);

    /**
     * Sets the current progress of the manager.
     */
    void setProgress(int progress);

    /**
     * Sets progress of the manager, based on a fractional value. <br>
     * Note: this also sets the {@link #setMaxProgress(int)} to {@literal 100}.
     */
    void setPercentageProgress(double progress);

    /**
     * Sets the current step to be shown to the user.
     */
    void setStep(String name);

    /**
     * Sets whether the max progress is known or not. If not, the loading bar will load 'forever'.
     */
    void setIndeterminate(boolean indeterminate);
}
