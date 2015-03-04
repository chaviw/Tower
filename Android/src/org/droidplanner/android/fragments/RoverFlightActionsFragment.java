package org.droidplanner.android.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.follow.FollowState;
import com.o3dr.services.android.lib.gcs.follow.FollowType;

import org.droidplanner.android.R;
import org.droidplanner.android.activities.helpers.SuperUI;
import org.droidplanner.android.dialogs.YesNoDialog;
import org.droidplanner.android.dialogs.YesNoWithPrefsDialog;
import org.droidplanner.android.fragments.helpers.ApiListenerFragment;
import org.droidplanner.android.utils.analytics.GAUtils;

/**
 * Created by Fredia Huya-Kouadio on 3/4/15.
 */
public class RoverFlightActionsFragment extends ApiListenerFragment implements FlightActionsFragment.SlidingUpHeader, View.OnClickListener {

    private static final String ACTION_FLIGHT_ACTION_BUTTON = "Rover flight action button";

    private static final IntentFilter intentFilter = new IntentFilter();
    static {
        intentFilter.addAction(AttributeEvent.STATE_ARMING);
        intentFilter.addAction(AttributeEvent.STATE_CONNECTED);
        intentFilter.addAction(AttributeEvent.STATE_DISCONNECTED);
        intentFilter.addAction(AttributeEvent.STATE_UPDATED);
        intentFilter.addAction(AttributeEvent.STATE_VEHICLE_MODE);
        intentFilter.addAction(AttributeEvent.FOLLOW_START);
        intentFilter.addAction(AttributeEvent.FOLLOW_STOP);
        intentFilter.addAction(AttributeEvent.FOLLOW_UPDATE);
    }

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.STATE_ARMING:
                case AttributeEvent.STATE_CONNECTED:
                case AttributeEvent.STATE_DISCONNECTED:
                case AttributeEvent.STATE_UPDATED:
                    setupButtonsByFlightState();
                    break;

                case AttributeEvent.STATE_VEHICLE_MODE:
                    updateFlightModeButtons();
                    break;

                case AttributeEvent.FOLLOW_START:
                case AttributeEvent.FOLLOW_STOP:
                    final FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
                    if (followState != null) {
                        String eventLabel = null;
                        switch (followState.getState()) {
                            case FollowState.STATE_START:
                                eventLabel = "FollowMe enabled";
                                break;

                            case FollowState.STATE_RUNNING:
                                eventLabel = "FollowMe running";
                                break;

                            case FollowState.STATE_END:
                                eventLabel = "FollowMe disabled";
                                break;

                            case FollowState.STATE_INVALID:
                                eventLabel = "FollowMe error: invalid state";
                                break;

                            case FollowState.STATE_DRONE_DISCONNECTED:
                                eventLabel = "FollowMe error: drone not connected";
                                break;

                            case FollowState.STATE_DRONE_NOT_ARMED:
                                eventLabel = "FollowMe error: drone not armed";
                                break;
                        }

                        if (eventLabel != null) {
                            HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                                    .setCategory(GAUtils.Category.FLIGHT)
                                    .setAction(ACTION_FLIGHT_ACTION_BUTTON)
                                    .setLabel(eventLabel);
                            GAUtils.sendEvent(eventBuilder);

                            Toast.makeText(getActivity(), eventLabel, Toast.LENGTH_SHORT).show();
                        }
                    }

                    /* FALL - THROUGH */
                case AttributeEvent.FOLLOW_UPDATE:
                    updateFlightModeButtons();
                    updateFollowButton();
                    break;
            }
        }
    };

    private View mDisconnectedButtons;
    private View disarmedButtons;
    private View armedButtons;
    private View mActiveButtons;

    private Button followBtn;
    private Button homeBtn;
    private Button pauseBtn;
    private Button autoBtn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rover_mission_control, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        mDisconnectedButtons = view.findViewById(R.id.mc_disconnected_buttons);
        disarmedButtons = view.findViewById(R.id.mc_disarmed_buttons);
        armedButtons = view.findViewById(R.id.mc_armed_buttons);
        mActiveButtons = view.findViewById(R.id.mc_connected_buttons);

        final Button connectBtn = (Button) view.findViewById(R.id.mc_connectBtn);
        connectBtn.setOnClickListener(this);

        homeBtn = (Button) view.findViewById(R.id.mc_homeBtn);
        homeBtn.setOnClickListener(this);

        final Button armBtn = (Button) view.findViewById(R.id.mc_armBtn);
        armBtn.setOnClickListener(this);

        final Button disarmBtn = (Button) view.findViewById(R.id.mc_disarmBtn);
        disarmBtn.setOnClickListener(this);

        final Button takeoffInAuto = (Button) view.findViewById(R.id.mc_startInAutoBtn);
        takeoffInAuto.setOnClickListener(this);

        pauseBtn = (Button) view.findViewById(R.id.mc_pause);
        pauseBtn.setOnClickListener(this);

        autoBtn = (Button) view.findViewById(R.id.mc_autoBtn);
        autoBtn.setOnClickListener(this);

        followBtn = (Button) view.findViewById(R.id.mc_follow);
        followBtn.setOnClickListener(this);
    }

    private void updateFollowButton() {
        final FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
        if (followState == null)
            return;

        switch (followState.getState()) {
            case FollowState.STATE_START:
                followBtn.setBackgroundColor(Color.RED);
                break;
            case FollowState.STATE_RUNNING:
                followBtn.setActivated(true);
                followBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;
            default:
                followBtn.setActivated(false);
                followBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;
        }
    }

    private void updateFlightModeButtons() {
        resetFlightModeButtons();

        final Drone drone = getDrone();
        final State droneState = drone.getAttribute(AttributeType.STATE);
        final VehicleMode flightMode = droneState.getVehicleMode();
        if (flightMode != null) {
            switch (flightMode) {
                case ROVER_AUTO:
                    autoBtn.setActivated(true);
                    break;

                case ROVER_GUIDED:
                    final GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
                    final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                    if (guidedState.isInitialized() && !followState.isEnabled()) {
                        pauseBtn.setActivated(true);
                    }
                    break;

                case ROVER_RTL:
                    homeBtn.setActivated(true);
                    break;
            }
        }
    }

    private void resetFlightModeButtons() {
        homeBtn.setActivated(false);
        pauseBtn.setActivated(false);
        autoBtn.setActivated(false);
    }

    private void setupButtonsByFlightState() {
        final State droneState = getDrone().getAttribute(AttributeType.STATE);
        if (droneState != null && droneState.isConnected()) {
            if (droneState.isArmed()) {
                if (droneState.isFlying()) {
                    setupButtonsForFlying();
                } else {
                    setupButtonsForArmed();
                }
            } else {
                setupButtonsForDisarmed();
            }
        } else {
            setupButtonsForDisconnected();
        }
    }

    private void resetButtonsContainerVisibility() {
        mDisconnectedButtons.setVisibility(View.GONE);
        disarmedButtons.setVisibility(View.GONE);
        armedButtons.setVisibility(View.GONE);
        mActiveButtons.setVisibility(View.GONE);
    }

    private void setupButtonsForDisconnected() {
        resetButtonsContainerVisibility();
        mDisconnectedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForDisarmed() {
        resetButtonsContainerVisibility();
        disarmedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForArmed() {
        resetButtonsContainerVisibility();
        armedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForFlying() {
        resetButtonsContainerVisibility();
        mActiveButtons.setVisibility(View.VISIBLE);
    }

    @Override
    public void onApiConnected() {
        setupButtonsByFlightState();
        updateFlightModeButtons();
        updateFollowButton();
        getBroadcastManager().registerReceiver(eventReceiver, intentFilter);
    }

    @Override
    public void onApiDisconnected() {
        getBroadcastManager().unregisterReceiver(eventReceiver);
    }

    private void getArmingConfirmation() {
        YesNoWithPrefsDialog ynd = YesNoWithPrefsDialog.newInstance(getActivity().getApplicationContext(),
                getString(R.string.dialog_confirm_arming_title),
                getString(R.string.dialog_confirm_rover_arming_msg), new YesNoDialog.Listener() {
                    @Override
                    public void onYes() {
                        getDrone().arm(true);
                    }

                    @Override
                    public void onNo() {
                    }
                }, getString(R.string.pref_warn_on_arm_key));

        if (ynd != null) {
            ynd.show(getChildFragmentManager(), "Confirm arming");
        }
    }

    @Override
    public boolean isSlidingUpPanelEnabled(Drone drone) {
        final State droneState = drone.getAttribute(AttributeType.STATE);
        return droneState.isConnected() && droneState.isArmed() && droneState.isFlying();
    }

    @Override
    public void onClick(View v) {
        final Drone drone = getDrone();
        HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                .setCategory(GAUtils.Category.FLIGHT);

        switch (v.getId()) {
            case R.id.mc_connectBtn:
                ((SuperUI) getActivity()).toggleDroneConnection();
                break;

            case R.id.mc_armBtn:
                getArmingConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Arm");
                break;

            case R.id.mc_disarmBtn:
                getDrone().arm(false);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Disarm");
                break;

            case R.id.mc_homeBtn:
                drone.changeVehicleMode(VehicleMode.ROVER_RTL);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.ROVER_RTL.getLabel());
                break;

            case R.id.mc_pause: {
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (followState.isEnabled()) {
                    drone.disableFollowMe();
                }

                drone.pauseAtCurrentLocation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Pause");
                break;
            }

            case R.id.mc_startInAutoBtn:
            case R.id.mc_autoBtn:
                drone.changeVehicleMode(VehicleMode.ROVER_AUTO);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.ROVER_AUTO.getLabel());
                break;

            case R.id.mc_follow: {
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (followState.isEnabled()) {
                    drone.disableFollowMe();
                } else
                    drone.enableFollowMe(FollowType.LEASH);
                break;
            }

            default:
                eventBuilder = null;
                break;
        }

        if (eventBuilder != null) {
            GAUtils.sendEvent(eventBuilder);
        }
    }
}
