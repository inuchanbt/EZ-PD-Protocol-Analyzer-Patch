# Third-party notice: JFreeChart 1.5.6

`jfreechart-1.5.6.jar` is the unmodified upstream JFreeChart 1.5.6 core JAR. The patcher stores it inside the Analyzer UI plug-in under the existing embedded entry name `lib/jfreechart-1.5.3.jar`; the established `jfreechart-swt-1.0.17.jar` SWT bridge remains in place.

JFreeChart is licensed under the GNU Lesser General Public License, version 2.1 or later (LGPL-2.1-or-later). Source code, license information, and the corresponding release are available from the [upstream JFreeChart project](https://github.com/jfree/jfreechart) and its [v1.5.6 release](https://github.com/jfree/jfreechart/releases/tag/v1.5.6).

The two source files under `src/org/jfree/` are original compatibility facades in this patch package. They preserve legacy JFreeChart API entry points used by the existing SWT bridge; they are not copied from JFreeChart.
