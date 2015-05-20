class R {


    static float getRandom(int[] seeds, int i1, int i2) {
	long s1 = seeds[i1] & 0xffffffffl;
	long s2 = seeds[i2] & 0xffffffffl;

	//seeds[i1] = 36969 * (seeds[i1] & 65535) + (seeds[i1] >> 16);
	//seeds[i2] = 18000 * (seeds[i2] & 65535) + (seeds[i2] >> 16);
	s1 = 36969 * (s1 & 65535) + (s1 >> 16) & 0xffffffff;
	s2 = 18000 * (s2 & 65535) + (s2 >> 16) & 0xffffffff;
	seeds[i1] = (int) (s1 & 0xffffffffl);
	seeds[i2] = (int) (s2 & 0xffffffffl);

	//System.out.printf("%d %d\n", seeds[i1] & 0xffffffffl, 
	//	seeds[i2] & 0xffffffffl);

	long ires = (s1 << 16 & 0xffffffffl) + s2 & 0xffffffffl;

	long res_ui = (ires & 0x007fffff) | 0x40000000;
	int res = (int) (res_ui & 0xffffffffl);
	//int res = (int) (s1 & 0xffffffffl);

	float r = (Float.intBitsToFloat(res) - 2.0f) / 2.0f;
	//float r = Float.intBitsToFloat(res);
	//System.out.printf("%f\n", r);
	return r;
    }
}
