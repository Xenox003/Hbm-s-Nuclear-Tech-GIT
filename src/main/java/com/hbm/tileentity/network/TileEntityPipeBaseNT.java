package com.hbm.tileentity.network;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import api.hbm.fluid.IFluidConductor;
import api.hbm.fluid.IPipeNet;
import api.hbm.fluid.PipeNet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityPipeBaseNT extends TileEntity implements IFluidConductor {
	
	private IPipeNet network;
	protected FluidType type = Fluids.NONE;

	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote && canUpdate()) {
			
			//we got here either because the net doesn't exist or because it's not valid, so that's safe to assume
			this.setPipeNet(type, null);
			
			this.connect();
			
			if(this.getPipeNet(type) == null) {
				this.setPipeNet(type, new PipeNet(type).joinLink(this));
			}
		}
	}
	
	public FluidType getType() {
		return this.type;
	}
	
	public void setType(FluidType type) {
		this.type = type;
		this.markDirty();
		
		if(worldObj instanceof WorldServer) {
			WorldServer world = (WorldServer) worldObj;
			world.getPlayerManager().markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}
	
	protected void connect() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			
			TileEntity te = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			
			if(te instanceof IFluidConductor) {
				
				IFluidConductor conductor = (IFluidConductor) te;
				
				if(!conductor.canConnect(type, dir.getOpposite()))
					continue;
				
				if(this.getPipeNet(type) == null && conductor.getPipeNet(type) != null) {
					conductor.getPipeNet(type).joinLink(this);
				}
				
				if(this.getPipeNet(type) != null && conductor.getPipeNet(type) != null && this.getPipeNet(type) != conductor.getPipeNet(type)) {
					conductor.getPipeNet(type).joinNetworks(this.getPipeNet(type));
				}
			}
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		
		if(!worldObj.isRemote) {
			if(this.network != null) {
				this.network.destroy();
			}
		}
	}

	/**
	 * Only update until a power net is formed, in >99% of the cases it should be the first tick. Everything else is handled by neighbors and the net itself.
	 */
	@Override
	public boolean canUpdate() {
		return (this.network == null || !this.network.isValid()) && !this.isInvalid();
	}

	@Override
	public long transferFluid(FluidType type, long fluid) {
		
		if(this.network == null)
			return fluid;
		
		return this.network.transferFluid(fluid);
	}

	@Override
	public long getDemand(FluidType type) {
		return 0;
	}

	@Override
	public IPipeNet getPipeNet(FluidType type) {
		return type == this.type ? this.network : null;
	}

	@Override
	public void setPipeNet(FluidType type, IPipeNet network) {
		this.network = network;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.type = Fluids.fromID(nbt.getInteger("type"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("type", this.type.getID());
	}
}