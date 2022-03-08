package com.lafi.cardgame.nazdarbaby.broadcast;

public interface BroadcastListener {

	void receiveBroadcast(String message);

	String getTableName();
}
