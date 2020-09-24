/*
 * Created on 19-Dec-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.disk.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.RealTimeInfo;

public class
DiskManagerRecheckScheduler
{
	private static Core core = CoreFactory.getSingleton();

	static int	 	strategy;
	static boolean 	smallest_first;
	static int		max_active;
	
    static{

    	 ParameterListener param_listener = new ParameterListener() {
    	    @Override
	        public void
			parameterChanged(
				String  str )
    	    {
    	    	strategy	 	= COConfigurationManager.getIntParameter( "diskmanager.hashchecking.strategy" );
    	   	    smallest_first	= COConfigurationManager.getBooleanParameter( "diskmanager.hashchecking.smallestfirst" );
    	   	    max_active		= COConfigurationManager.getIntParameter( "diskmanager.hashchecking.maxactive" );
    	   	      
    	   	    if ( max_active <= 0 ){
    	   	    	  
    	   	    	max_active = Integer.MAX_VALUE;
    	   	    }
    	    }
    	 };

 		COConfigurationManager.addAndFireParameterListeners(
 				new String[]{
 					"diskmanager.hashchecking.strategy",
 					"diskmanager.hashchecking.smallestfirst",
 					"diskmanager.hashchecking.maxactive"},
 				param_listener );
    }

	private final List<Object[]>		instances		= new ArrayList<>();
	private final AEMonitor				instance_mon	= new AEMonitor( "DiskManagerRecheckScheduler" );

	public DiskManagerRecheckInstance
	register(
		DiskManagerHelper	helper,
		boolean				low_priority )
	{
		CoreOperationTask.ProgressCallback progress = 
				new ProgressCallback(){
					
					@Override
					public void setTaskState(int state){
					}
					
					@Override
					public int getSupportedTaskStates(){
						return( 0 );
					}
					
					@Override
					public String getSubTaskName(){
						return null;
					}
					
					@Override
					public int 
					getProgress()
					{
						return( helper.getCompleteRecheckStatus());
					}
				};
				
			CoreOperationTask task =
				new CoreOperationTask()
				{
					public String
					getName()
					{
						return( helper.getDisplayName());
					}
					
					public void
					run(
						CoreOperation operation )
					{
					}
					
					public ProgressCallback
					getProgressCallback()
					{
						return( progress );
					}
				};
				
			CoreOperation op = 
				new CoreOperation()
				{
					public int
					getOperationType()
					{
						return( CoreOperation.OP_DOWNLOAD_CHECKING );
					}
		
					public CoreOperationTask
					getTask()
					{
						return( task );
					}
				};
		try{

					
			instance_mon.enter();

			DiskManagerRecheckInstance	res =
				new DiskManagerRecheckInstance(
						this,
						helper.getTorrent().getSize(),
						(int)helper.getTorrent().getPieceLength(),
						low_priority );

			instances.add( new Object[]{ res, op });

			core.addOperation( op );
			
			if ( smallest_first ){

				Collections.sort(
						instances,
						new Comparator<Object[]>()
						{
							@Override
							public int
							compare(
								Object[] 	o1,
								Object[]	o2 )
							{
								long	comp = ((DiskManagerRecheckInstance)o1[0]).getMetric() - ((DiskManagerRecheckInstance)o2[0]).getMetric();

								if ( comp < 0 ){

									return( -1 );

								}else if ( comp == 0 ){

									return( 0 );

								}else{
									return( 1 );
								}
							}
						});
			}

			return( res );

		}finally{

			instance_mon.exit();
		}
	}

	public int
	getPieceConcurrency(
		DiskManagerRecheckInstance	instance )
	{
		int piece_length = instance.getPieceLength();
		
		if ( strategy <= 1 ){
		
			return( piece_length>32*1024*1024?1:2 );
			
		}else{
			
				// limit to 32MB
			
			int num = 32*1024*1024/piece_length;
			
			return( Math.min( 8, num ));
		}
	}
	
	protected boolean
	getPermission(
		DiskManagerRecheckInstance	instance )
	{
		boolean	result 	= false;
		int		delay	= 250;

		try{
			instance_mon.enter();

			for ( int i=0;i<Math.min( max_active, instances.size());i++){
				
				if ( instances.get(i)[0] == instance ){
	
					boolean	low_priority = instance.isLowPriority();
	
						// defer low priority activities if we are running a real-time task
	
					if ( low_priority && RealTimeInfo.isRealTimeTaskActive()){
	
						result = false;
	
					}else{
	
			            if ( strategy == 0 ){
	
			            	delay	= 0;	// delay introduced elsewhere
	
			            }else if ( strategy != 1 || !low_priority ){
	
			            	delay	= 1;	// high priority recheck, just a smidge of a delay
	
			            }else{
	
				            	//delay a bit normally anyway, as we don't want to kill the user's system
				            	//during the post-completion check (10k of piece = 1ms of sleep)
	
			            	delay = instance.getPieceLength() /1024 /10;
	
			            	delay = Math.min( delay, 409 );
	
			            	delay = Math.max( delay, 12 );
		  				}
	
			            result	= true;
					}
					
					break;
				}
			}
		}finally{

			instance_mon.exit();
		}

		if ( delay > 0 ){

			try{
				Thread.sleep( delay );

			}catch( Throwable e ){

			}
		}

		return( result );
	}

	protected void
	unregister(
		DiskManagerRecheckInstance	instance )
	{
		try{
			instance_mon.enter();

			Iterator<Object[]>	it = instances.iterator();
			
			while( it.hasNext()){
			
				Object[] entry = it.next();
				
				if ( entry[0] == instance ){
					
					it.remove();
					
					core.removeOperation((CoreOperation)entry[1]);
					
					break;
				}
			}			
		}finally{

			instance_mon.exit();
		}
	}
}
