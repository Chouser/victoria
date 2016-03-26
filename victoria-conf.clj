{:servo-dev "dev-servoblaster"
 :servo-delay 100 ;; milliseconds

 :sails {:viz-range [80 15] ;; degrees
         ;; Sails: Power HD HD-1201MG, 500-2500us, ~170deg
         ;; TODO adjust after sheets are rigged:
         :servo-range [1000 1770]} ;; microseconds
 :rudder {:viz-range [30 -30] ;; degrees
          ;; Rudder: Futaba S3003, 400-2400us, ~180deg
          ;; TODO adjust after pusher is mounted:
          :servo-range [1067 1733]}}
