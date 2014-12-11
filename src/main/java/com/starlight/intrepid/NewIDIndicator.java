package com.starlight.intrepid;

/**
 *
 */
class NewIDIndicator extends Throwable {
	private final VMID new_vmid;
	private final int new_object_id;
	private final Object result;
	private final boolean is_thrown;

	NewIDIndicator( VMID new_vmid, int new_object_id, Object result, boolean is_thrown ) {
		this.new_vmid = new_vmid;
		this.new_object_id = new_object_id;
		this.result = result;
		this.is_thrown = is_thrown;
	}

	public VMID getNewVMID() {
		return new_vmid;
	}

	public int getNewObjectID() {
		return new_object_id;
	}

	public Object getResult() {
		return result;
	}

	public boolean isThrown() {
		return is_thrown;
	}
}
