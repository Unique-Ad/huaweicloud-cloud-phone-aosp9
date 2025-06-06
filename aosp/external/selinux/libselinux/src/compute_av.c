#include <unistd.h>
#include <sys/types.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <limits.h>
#include "selinux_internal.h"
#include "policy.h"
#include "mapping.h"
#include "avc_internal.h"

int security_compute_av_flags_raw(const char * scon,
				  const char * tcon,
				  security_class_t tclass,
				  access_vector_t requested,
				  struct av_decision *avd)
{
	char path[PATH_MAX];
	char *buf;
	size_t len;
	int fd, ret;

	if (!selinux_mnt) {
		errno = ENOENT;
		return -1;
	}

	snprintf(path, sizeof path, "%s/access", selinux_mnt);
	fd = open(path, O_RDWR | O_CLOEXEC);
	if (fd < 0)
		return -1;

	len = selinux_page_size;
	buf = malloc(len);
	if (!buf) {
		ret = -1;
		goto out;
	}

	snprintf(buf, len, "%s %s %hu %x", scon, tcon,
		 unmap_class(tclass), unmap_perm(tclass, requested));

	ret = write(fd, buf, strlen(buf));
	if (ret < 0) {
		avc_log(SELINUX_ERROR, "%s %s write %s to %s failed. %d %d", scon, tcon, buf, path, ret, errno);
		goto out2;
	}

	memset(buf, 0, len);
	ret = read(fd, buf, len - 1);
	if (ret < 0)
		goto out2;

	ret = sscanf(buf, "%x %x %x %x %u %x",
		     &avd->allowed, &avd->decided,
		     &avd->auditallow, &avd->auditdeny,
		     &avd->seqno, &avd->flags);
	if (ret < 5) {
		ret = -1;
		goto out2;
	} else if (ret < 6)
		avd->flags = 0;

	/* If tclass invalid, kernel sets avd according to deny_unknown flag */
	if (tclass != 0)
		map_decision(tclass, avd);

	ret = 0;
      out2:
	free(buf);
      out:
	close(fd);
	return ret;
}

hidden_def(security_compute_av_flags_raw)

int security_compute_av_raw(const char * scon,
			    const char * tcon,
			    security_class_t tclass,
			    access_vector_t requested,
			    struct av_decision *avd)
{
	struct av_decision lavd;
	int ret;

	ret = security_compute_av_flags_raw(scon, tcon, tclass,
					    requested, &lavd);
	if (ret == 0) {
		avd->allowed = lavd.allowed;
		avd->decided = lavd.decided;
		avd->auditallow = lavd.auditallow;
		avd->auditdeny = lavd.auditdeny;
		avd->seqno = lavd.seqno;
		/* NOTE:
		 * We should not return avd->flags via the interface
		 * due to the binary compatibility.
		 */
	}
	return ret;
}

hidden_def(security_compute_av_raw)

int security_compute_av_flags(const char * scon,
			      const char * tcon,
			      security_class_t tclass,
			      access_vector_t requested,
			      struct av_decision *avd)
{
	char * rscon;
	char * rtcon;
	int ret;

	if (selinux_trans_to_raw_context(scon, &rscon))
		return -1;
	if (selinux_trans_to_raw_context(tcon, &rtcon)) {
		freecon(rscon);
		return -1;
	}
	ret = security_compute_av_flags_raw(rscon, rtcon, tclass,
					    requested, avd);

	freecon(rscon);
	freecon(rtcon);

	return ret;
}

hidden_def(security_compute_av_flags)

int security_compute_av(const char * scon,
			const char * tcon,
			security_class_t tclass,
			access_vector_t requested, struct av_decision *avd)
{
	struct av_decision lavd;
	int ret;

	ret = security_compute_av_flags(scon, tcon, tclass,
					requested, &lavd);
	if (ret == 0)
	{
		avd->allowed = lavd.allowed;
		avd->decided = lavd.decided;
		avd->auditallow = lavd.auditallow;
		avd->auditdeny = lavd.auditdeny;
		avd->seqno = lavd.seqno;
		/* NOTE:
		 * We should not return avd->flags via the interface
		 * due to the binary compatibility.
		 */
	}

	return ret;
}

hidden_def(security_compute_av)
