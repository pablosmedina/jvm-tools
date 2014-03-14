
public class Test {

	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		while(true) {
			Thread.sleep(1);
			method1();
			method2();
			method3();
		}

	}

	private static void method3() throws InterruptedException {
//		Thread.sleep(1);
	}

	private static void method2() {
		int j = 0;
		for(int i = 1 ; i <= 100000000 ; i++) {
			j = j + i;
		}
	}

	private static void method1() {
		for(int h = 1 ; h <= 100000000 ; h++) { {
			int j = 0;
			for(int i = 1 ; i <= 100000000 ; i++) {
				j = j * i;
			}
		}
	}}

}
