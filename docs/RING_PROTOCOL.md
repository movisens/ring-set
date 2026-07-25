# Ring BLE protocol findings

This note records protocol details confirmed against the connected ring on
2026-07-26. They are important because live heart-rate measurement can appear to
work while stored heart-rate history silently fails to transfer.

## Packet checksum

Commands on the Nordic-UART-style channel are 16 bytes:

```text
[command, payload..., zero padding..., checksum]
```

The checksum is the unsigned sum of bytes 0 through 14, wrapped modulo 256:

```text
checksum = sum(packet[0..14]) & 0xff
```

Do **not** use modulo 255. For example, `0x9e + 0xee` wraps to `0x8c`.

This bug was deceptive: simple live-HR commands have small byte sums, so modulo
255 and modulo 256 produce the same result. Timestamped history requests often
cross 255, causing the ring to reject or ignore them even though live measurement
and its green LED still work.

## Ring clock

The set-time command is:

```text
0x01 <BCD yy> <BCD mm> <BCD dd> <BCD HH> <BCD MM> <BCD ss>
```

The remaining payload bytes stay zero before the checksum. Do not append a
language byte after the seconds; some firmware treats it as date state.

The app sets the ring clock after each successful connection and after a data
sync. The Control tab also provides a manual **Check & set now** action. This
firmware has no read-clock command, so "check" means writing the current phone
time and waiting for the ring's acknowledgement.

## Heart-rate history request

HR history uses command `0x15` followed by a four-byte little-endian timestamp.
The timestamp is not a normal UTC Unix timestamp. It is a timezone-less local
wall-clock value encoded as though the local time were UTC:

```text
wire_epoch = unix_epoch + zone_offset + daylight_saving_offset
```

For example, a request for local midnight must encode local midnight's wall
clock fields, including the DST offset that applies on that date. Converting a
UTC epoch directly makes the requested day or time wrong.

The response format observed on this ring is:

- Packet index `0`: byte 2 is the packet count and byte 3 is the sample interval.
- Packet index `1`: bytes 2-5 echo the local-wall epoch; HR samples begin at byte 6.
- Later packets: HR samples occupy bytes 2-14.
- A zero sample means no reading for that slot.
- Packet index `0xff` means no history for the requested day.

Although the logging setting accepts other minute values, stored HR history on
this firmware is returned in fixed five-minute slots. Automatic sync therefore
runs no more frequently than every five minutes when phone-side passive logging
is disabled.

The request already identifies the phone-local day. If a ring has lost power,
the echoed day can be stale even after the clock is reset. The parser assigns
samples to the requested local day and logs a warning when the echoed wire day
does not match.

## Verified result

After correcting the checksum and local-wall timestamp on 2026-07-26, a sync
returned 47 ring HR samples, including:

```text
2026-07-26 00:00  67 bpm  source=ring
2026-07-26 00:05  81 bpm  source=ring
```

The one-hour Stats graph then showed the ring-origin samples. A green sensor LED
alone only confirms that the ring measured; it does not confirm that the history
request was accepted or that data reached the phone.

For diagnosis, filter Android logs by `RingHrHistory`. A healthy request logs the
outgoing local start and wire epoch, then a history header with a non-zero packet
count, followed by the received `0x15` packets.

## Data merge behavior

Synced device history is stored with `source=ring`. Phone-clocked live
measurements are stored with `source=app`. Inserts from ring history do not
replace an existing app-origin sample, so a later ring sync cannot erase data
recorded directly by the app.
