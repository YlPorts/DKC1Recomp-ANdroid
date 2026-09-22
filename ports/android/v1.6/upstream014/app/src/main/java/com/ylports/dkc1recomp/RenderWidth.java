package com.ylports.dkc1recomp;

/**
 * Chooses the native SNES source width used by the shared widescreen renderer.
 *
 * The upstream 0.0.14 runtime accepts even widths from 256 through 448.  The
 * Android host keeps the SNES 7:6 pixel aspect, so this changes how much world
 * is rendered instead of stretching the 256x224 picture to the phone shape.
 */
public final class RenderWidth {
    public static final int NATIVE=256, WIDE_16_10=308, WIDE_16_9=342, MAX=448;

    private RenderWidth() {}

    public static int forAspect(String aspect,int screenWidth,int screenHeight){
        if("16:10".equals(aspect))return WIDE_16_10;
        if("16:9".equals(aspect))return WIDE_16_9;
        if("21:9".equals(aspect))return MAX;
        if("device".equals(aspect))return forDisplay(screenWidth,screenHeight);
        return NATIVE;
    }

    /**
     * Convert the physical display ratio to SNES source pixels.  SNES pixels
     * are displayed at 7:6, hence sourceWidth ~= displayAspect * 224 * 6/7.
     * Width is kept even because the shared runtime uses symmetric margins.
     */
    public static int forDisplay(int width,int height){
        if(width<=0||height<=0)return WIDE_16_9;
        int longSide=Math.max(width,height),shortSide=Math.min(width,height);
        double wanted=(double)longSide/(double)shortSide*224.0*6.0/7.0;
        int result=(int)Math.round(wanted);
        if((result&1)!=0)result++;
        if(result<NATIVE)result=NATIVE;
        if(result>MAX)result=MAX;
        return result;
    }
}