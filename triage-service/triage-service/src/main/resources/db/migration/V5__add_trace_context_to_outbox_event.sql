-- W3C trace context of the request that produced the event, so its consumers continue the same trace (NFR-16).
-- NULL when the change ran outside a trace. The event payload and its correlation id are unchanged.
ALTER TABLE outbox_evento ADD COLUMN traceparent VARCHAR(55);
