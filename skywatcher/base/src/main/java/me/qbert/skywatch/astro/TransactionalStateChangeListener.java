package me.qbert.skywatch.astro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import me.qbert.skywatch.listeners.ObjectStateChangeListener;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

public class TransactionalStateChangeListener implements ObjectStateChangeListener {
	private ArrayList<ObjectStateChangeListener> listeners = new ArrayList<ObjectStateChangeListener>();
	private HashMap<ObjectStateChangeListener, Object> transactions = new HashMap<ObjectStateChangeListener, Object>();
	private boolean transactionStarted = false;
	
	public void addListener(ObjectStateChangeListener stateChangeListener) {
		if (! listeners.contains(stateChangeListener))
			listeners.add(stateChangeListener);
	}

	public void begin() {
		transactions.clear();
		transactionStarted = true;
	}
	
	public void commit() {
		if (! transactionStarted)
			return;
		
		transactionStarted = false;
		Set<ObjectStateChangeListener> keys = transactions.keySet();
		for (ObjectStateChangeListener transaction : keys) {
			Object source = transactions.get(transaction);
			if (source != null) {
				for (ObjectStateChangeListener listener : listeners)
					listener.stateChanged(source, listener);
			}
		}
	}
	
	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		if (transactionStarted)
			transactions.put(listener, source);
		else if (listener != this)
			listener.stateChanged(source, listener);
	}

}
