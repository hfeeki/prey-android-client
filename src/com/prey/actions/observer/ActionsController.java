/*******************************************************************************
 * Created by Carlos Yaconi
 * Copyright 2012 Fork Ltd. All rights reserved.
 * License: GPLv3
 * Full license at "/LICENSE"
 ******************************************************************************/
package com.prey.actions.observer;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.prey.PreyLogger;
import com.prey.actions.HttpDataService;
import com.prey.actions.PreyAction;
import com.prey.actions.PreyExecutionWaitNotify;

import com.prey.net.PreyWebServices;
import com.prey.util.ClassUtil;

public class ActionsController {

	private static ActionsController _instance;
	private JobsQueue actionJobs; // id(long) - ActionJob
	private Context ctx;
	private ArrayList<PreyAction> lastReceivedActions;
	private PreyExecutionWaitNotify waitNotify;

	private ActionsController(Context ctx) {
		this.actionJobs = new JobsQueue(this);
		this.ctx = ctx;
	}

	public static ActionsController getInstance(Context ctx) {
		if (_instance == null)
			_instance = new ActionsController(ctx);
		return _instance;
	}

	/**
	 * This method checks current running modules against to a list of modules.
	 * If there are some modules running that don't appear on the list, then
	 * them should be killed. Use case: Lock module. Should be killed if user
	 * unselect it in the control panel.
	 * 
	 * @param actions
	 *            List of active modules received from the Control Panel.
	 */
	public void stopUnselectedModules(ArrayList<PreyAction> actions) {
		PreyLogger.d("Checking if there are modules to stop.");
		if (lastReceivedActions != null) { // If first running
			for (PreyAction probablyRunningAction : lastReceivedActions) {
				boolean killAction = true;
				for (PreyAction controlPanelAction : actions) {
					PreyLogger.d("Checking control panel action: " + controlPanelAction.ID + " against probably running action: " + probablyRunningAction.ID);
					if (probablyRunningAction.equals(controlPanelAction)) {
						killAction = false;
						PreyLogger.d("Matched!, no need to kill it.");
						break;
					}
				}
				if (killAction)
					probablyRunningAction.killAnyInstanceRunning(ctx);
			}
		} else
			this.lastReceivedActions = actions;

	}

	/**
	 * Run a group of actions (modules). Usually this group contains the actions
	 * provided by the XML coming from control panel.
	 * 
	 * @param actions
	 */
	public void runActionGroup(ArrayList<PreyAction> actions, PreyExecutionWaitNotify waitNotify, boolean isMissing) {
		this.waitNotify = waitNotify;
		JobsGroup jobsGroup = new JobsGroup(actions, this.ctx);
		this.actionJobs.addAndRunJobGroup(jobsGroup, this.ctx, isMissing);
		if (!jobsGroup.hasReportModules())
			waitNotify.doNotify();
	}

	public void finishRunningJosb() {
		this.actionJobs.finishRunningJobs(this.ctx);
	}

	public void jobGroupFinished(ArrayList<ActionResult> results, Context ctx) {
		ArrayList<HttpDataService> dataToBeSent = new ArrayList<HttpDataService>();
		if (results.size() > 0) {
			for (ActionResult aResult : results) {
				dataToBeSent.add(aResult.getDataToSend());
			}
			PreyWebServices.getInstance().sendPreyHttpReport(ctx, dataToBeSent);
		}
		waitNotify.doNotify();
	}

	public List<HttpDataService> runActionJson(Context ctx, List<JSONObject> jsonObjectList) {
		List<HttpDataService> listData=new ArrayList<HttpDataService>();
		
		PreyLogger.i("tamanio:"+(jsonObjectList==null?-1:jsonObjectList.size()));
		try {
			for(int i=0;jsonObjectList!=null&&i<jsonObjectList.size();i++){
				JSONObject jsonObject=jsonObjectList.get(i);
				String nameAction = jsonObject.getString("target");
				String methodAction = jsonObject.getString("command");
				JSONObject parametersAction =null;
				try{
					parametersAction = jsonObject.getJSONObject("options");
				}catch(JSONException e){
					
				}
				PreyLogger.i("nameAction:"+nameAction+" methodAction:"+methodAction);
						 
				List<ActionResult> lista = new ArrayList<ActionResult>();
				listData=ClassUtil.execute(ctx, lista, nameAction, methodAction, parametersAction,listData);
				if (lista.size() > 0) {
					ArrayList<HttpDataService> dataToBeSent = new ArrayList<HttpDataService>();
					for (ActionResult result : lista) {
						dataToBeSent.add(result.getDataToSend());
					}
					PreyWebServices.getInstance().sendPreyHttpReport(ctx, dataToBeSent);
				}
			}
			return listData;
		} catch (JSONException e) {
			PreyLogger.e("Error, causa:" + e.getMessage(), e);
		}
		return null;
	}
}
