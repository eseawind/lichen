/*		
 * Copyright 2013-5-13 The EGF Co., Ltd. 
 * site: http://www.egfit.com
 * file: $Id: org.eclipse.jdt.ui.prefs,Bitmap.java,fangj Exp$
 * created at:下午01:48:09
 */
package com.egf.db.services.impl;

import com.egf.db.core.define.IndexType;

/**
 * @author fangj
 * @version $Revision: 1.0 $
 * @since 1.0
 */
class Bitmap implements IndexType {
	
	public String getIndexType() {
		return "Bitmap";
	}

}
