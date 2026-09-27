package com.example.codenection2026_package.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * The triage sheet opened by the dashboard's Rebalance button.
 *
 * <p>Port of the #triageModal block in Prototype/dashboard.html: two impact metrics,
 * then the four ways the optimiser can treat an item (protected, kept, postponed,
 * deferred), then the accept and dismiss actions.
 *
 * <p>Accepting dismisses the sheet and fires {@link OnRebalanceAccepted}, which the
 * dashboard uses to show its "Schedule Rebalanced" toast. That is the prototype's
 * acceptRebalance() -> closeTriageModal() + showToast() chain.
 *
 * <p>The sheet itself has no data dependency: every row is a fixed proposal, so it
 * renders on its own without the Room layer.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class TriageSheetFragment extends BottomSheetDialogFragment {

    /** Implemented by the dashboard so the sheet can report a confirmed rebalance. */
    public interface OnRebalanceAccepted {
        void onRebalanceAccepted();
    }

    /** Tag used by DashboardFragment when it shows this sheet. */
    public static final String TAG = "triage";

    /**
     * Held as a plain field rather than an argument: this dialog is transient, and if
     * the process is killed while it is up, Android restores it without a callback,
     * which simply means no toast. Documented rather than worked around.
     */
    @Nullable
    private OnRebalanceAccepted listener;

    public TriageSheetFragment() {
        super(R.layout.sheet_triage);
    }

    /** Sets the callback fired after the user accepts the proposal. */
    public void setOnRebalanceAccepted(@Nullable OnRebalanceAccepted listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_triage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView dino = view.findViewById(R.id.triageDino);
        if (dino != null) {
            // Animated GIF asset, so it must go through Glide.
            Glide.with(this).load(R.drawable.dino_happy).into(dino);
        }

        View close = view.findViewById(R.id.triageClose);
        if (close != null) {
            close.setOnClickListener(v -> dismiss());
        }

        View dismissButton = view.findViewById(R.id.triageDismiss);
        if (dismissButton != null) {
            dismissButton.setOnClickListener(v -> dismiss());
        }

        View accept = view.findViewById(R.id.triageAccept);
        if (accept != null) {
            accept.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onRebalanceAccepted();
                }
            });
        }
    }
}
